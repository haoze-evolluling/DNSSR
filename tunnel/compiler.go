package tunnel

import (
	"bufio"
	"encoding/binary"
	"fmt"
	"net"
	"os"
	"sort"
	"strings"
)

// ─────────────────────────────────────────────────────────────────────────────
// Local Filter Compiler — builds .trie and .bloom files on-device.
//
// Used as fallback for custom filters when the backend API is unreachable.
// Parses domain lists (hosts, AdBlock, plain) and produces binary files
// compatible with MmapTrie and BloomFilter readers.
// ─────────────────────────────────────────────────────────────────────────────

// CompileFilterList downloads a filter list from rawPath (local file path),
// parses domains, and writes .trie and .bloom files.
// Returns the number of domains compiled, or an error.
//
// This is exported for gomobile and called from Kotlin.
func CompileFilterList(inputPath, triePath, bloomPath string) (int, error) {
	f, err := os.Open(inputPath)
	if err != nil {
		return 0, fmt.Errorf("open input: %w", err)
	}
	defer f.Close()

	// Pass 1: collect unique domains (streaming, line-by-line)
	domains := make(map[string]struct{})
	scanner := bufio.NewScanner(f)
	scanner.Buffer(make([]byte, 256*1024), 256*1024)
	for scanner.Scan() {
		d := parseDomainLine(scanner.Text())
		if d != "" {
			domains[d] = struct{}{}
		}
	}
	if err := scanner.Err(); err != nil {
		return 0, fmt.Errorf("scan input: %w", err)
	}

	count := len(domains)
	if count == 0 {
		return 0, fmt.Errorf("no valid domains found in filter list")
	}

	// Build trie
	root := newTrieBuilderNode()
	for d := range domains {
		root.insert(d)
	}
	if err := root.saveToFile(triePath); err != nil {
		return 0, fmt.Errorf("write trie: %w", err)
	}

	// Build bloom filter
	bloom := NewBloomBuilder(count, 0.001)
	for d := range domains {
		bloom.Add(d)
	}
	if err := bloom.SaveToFile(bloomPath); err != nil {
		return 0, fmt.Errorf("write bloom: %w", err)
	}

	logf("Compiled %d domains → %s + %s", count, triePath, bloomPath)
	return count, nil
}

// ─────────────────────────────────────────────────────────────────────────────
// Domain Parser — handles hosts, AdBlock Plus, and plain domain formats
// ─────────────────────────────────────────────────────────────────────────────

func parseDomainLine(line string) string {
	line = strings.TrimSpace(line)

	// Skip empty lines and comments
	if line == "" || line[0] == '#' || line[0] == '!' {
		return ""
	}

	// Skip AdBlock exception rules (@@)
	if strings.HasPrefix(line, "@@") {
		return ""
	}

	var domain string

	switch {
	// AdBlock Plus format: ||domain.com^
	case strings.HasPrefix(line, "||"):
		domain = strings.TrimPrefix(line, "||")
		// Reject rules with wildcards/paths
		if strings.ContainsAny(domain, "/*?") {
			return ""
		}
		if idx := strings.IndexByte(domain, '^'); idx != -1 {
			domain = domain[:idx]
		}

	// Hosts file format: 0.0.0.0 domain.com or 127.0.0.1 domain.com
	case strings.HasPrefix(line, "0.0.0.0 "),
		strings.HasPrefix(line, "127.0.0.1 "):
		fields := strings.Fields(line)
		if len(fields) >= 2 {
			domain = fields[1]
		}
		// Strip inline comments
		if idx := strings.IndexByte(domain, '#'); idx != -1 {
			domain = domain[:idx]
		}

	// Plain domain (one per line, must contain a dot, no spaces)
	default:
		if !strings.ContainsAny(line, " \t") && strings.Contains(line, ".") {
			domain = line
		}
	}

	domain = strings.TrimSpace(strings.ToLower(domain))

	// Validate
	if domain == "" || !strings.Contains(domain, ".") {
		return ""
	}
	if domain == "localhost" || domain == "localhost.localdomain" ||
		domain == "local" || domain == "broadcasthost" {
		return ""
	}
	// Reject IP addresses
	if net.ParseIP(domain) != nil {
		return ""
	}

	return domain
}

// ─────────────────────────────────────────────────────────────────────────────
// Trie Builder — reversed-label trie with BFS binary serialization
// ─────────────────────────────────────────────────────────────────────────────

type trieBuilderNode struct {
	children   map[string]*trieBuilderNode
	isTerminal bool
}

func newTrieBuilderNode() *trieBuilderNode {
	return &trieBuilderNode{children: make(map[string]*trieBuilderNode)}
}

// insert adds a domain to the trie with reversed labels.
// "ads.google.com" → [com][google][ads]
func (n *trieBuilderNode) insert(domain string) {
	labels := strings.Split(domain, ".")
	node := n
	for i := len(labels) - 1; i >= 0; i-- {
		label := labels[i]
		child, ok := node.children[label]
		if !ok {
			child = newTrieBuilderNode()
			node.children[label] = child
		}
		node = child
	}
	node.isTerminal = true
}

// saveToFile serializes the trie to the binary format compatible with MmapTrie.
func (n *trieBuilderNode) saveToFile(path string) error {
	// BFS to assign offsets
	type bfsEntry struct {
		node   *trieBuilderNode
		offset int
	}

	// Count nodes and domains
	nodeCount := 0
	domainCount := 0
	var countNodes func(node *trieBuilderNode)
	countNodes = func(node *trieBuilderNode) {
		nodeCount++
		if node.isTerminal {
			domainCount++
		}
		for _, child := range node.children {
			countNodes(child)
		}
	}
	countNodes(n)

	// BFS pass 1: calculate byte offsets
	queue := []bfsEntry{{node: n, offset: headerSize}}
	currentOffset := headerSize
	offsets := make(map[*trieBuilderNode]int)

	for len(queue) > 0 {
		entry := queue[0]
		queue = queue[1:]

		offsets[entry.node] = currentOffset

		// Node size: isTerminal(1) + childCount(4) + children
		nodeSize := 1 + 4
		sortedLabels := sortedKeys(entry.node.children)
		for _, label := range sortedLabels {
			nodeSize += 2 + len(label) + 4 // labelLen(2) + label(N) + childOffset(4)
		}
		currentOffset += nodeSize

		for _, label := range sortedLabels {
			child := entry.node.children[label]
			queue = append(queue, bfsEntry{node: child})
		}
	}

	// BFS pass 2: write binary data
	f, err := os.Create(path)
	if err != nil {
		return fmt.Errorf("create trie file: %w", err)
	}
	defer f.Close()

	// Write header
	header := make([]byte, headerSize)
	binary.BigEndian.PutUint32(header[0:4], trieMagic)
	binary.BigEndian.PutUint32(header[4:8], trieVersion)
	binary.BigEndian.PutUint32(header[8:12], uint32(nodeCount))
	binary.BigEndian.PutUint32(header[12:16], uint32(domainCount))
	if _, err := f.Write(header); err != nil {
		return err
	}

	// BFS write nodes
	queue2 := []*trieBuilderNode{n}
	for len(queue2) > 0 {
		node := queue2[0]
		queue2 = queue2[1:]

		// isTerminal (1 byte)
		if node.isTerminal {
			f.Write([]byte{1})
		} else {
			f.Write([]byte{0})
		}

		// childCount (4 bytes)
		sortedLabels := sortedKeys(node.children)
		countBuf := make([]byte, 4)
		binary.BigEndian.PutUint32(countBuf, uint32(len(sortedLabels)))
		f.Write(countBuf)

		// Children: labelLen(2) + label(N) + childOffset(4)
		for _, label := range sortedLabels {
			child := node.children[label]

			lenBuf := make([]byte, 2)
			binary.BigEndian.PutUint16(lenBuf, uint16(len(label)))
			f.Write(lenBuf)
			f.Write([]byte(label))

			offBuf := make([]byte, 4)
			binary.BigEndian.PutUint32(offBuf, uint32(offsets[child]))
			f.Write(offBuf)
		}

		for _, label := range sortedLabels {
			queue2 = append(queue2, node.children[label])
		}
	}

	return nil
}

func sortedKeys(m map[string]*trieBuilderNode) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}
