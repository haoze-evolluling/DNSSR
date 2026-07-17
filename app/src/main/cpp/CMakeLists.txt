cmake_minimum_required(VERSION 3.22.1)
project(dnssr_traffic_forwarder C)

add_library(
    dnssr_traffic_forwarder
    SHARED
    native_traffic_forwarder.c
    third_party/zdtun/zdtun.c
    third_party/zdtun/utils.c
)

target_compile_definitions(dnssr_traffic_forwarder PRIVATE ANDROID NO_DEBUG)
target_compile_options(dnssr_traffic_forwarder PRIVATE -Wall -Wextra -Werror=return-type)
target_include_directories(
    dnssr_traffic_forwarder
    PRIVATE
    third_party/zdtun
    third_party/zdtun/third_party
)

find_library(log_library log)
target_link_libraries(dnssr_traffic_forwarder ${log_library})
