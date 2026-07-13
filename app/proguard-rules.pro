# 基础保留规则
-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*

# Room 实体类（保险起见，避免被 R8 误删）
-keep class com.haoze.dnssr.data.entity.** { *; }

# 使用 org.json 手动序列化的数据类
-keepclassmembers class com.haoze.dnssr.vpn.DnsProvider { *; }
-keepclassmembers class com.haoze.dnssr.vpn.DnsProtocol { *; }
