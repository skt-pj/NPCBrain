if (NOT DEFINED LLAMA_SOURCE_DIR)
    message(FATAL_ERROR "LLAMA_SOURCE_DIR is required")
endif()

set(_llama_vulkan_source "${LLAMA_SOURCE_DIR}/ggml/src/ggml-vulkan/ggml-vulkan.cpp")
file(READ "${_llama_vulkan_source}" _source)

set(_anchor [=[
}
static vk_device_architecture get_device_architecture(const vk::PhysicalDevice& device) {
]=])
set(_helper [=[
}

static void ggml_vk_get_physical_device_features2(
        VkPhysicalDevice physical_device,
        VkPhysicalDeviceFeatures2 * features) {
    auto get_features2 = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(
            vkGetInstanceProcAddr(vk_instance.instance, "vkGetPhysicalDeviceFeatures2"));
    if (get_features2 == nullptr) {
        get_features2 = reinterpret_cast<PFN_vkGetPhysicalDeviceFeatures2>(
                vkGetInstanceProcAddr(vk_instance.instance, "vkGetPhysicalDeviceFeatures2KHR"));
    }
    if (get_features2 == nullptr) {
        throw vk::SystemError(
                vk::Result::eErrorExtensionNotPresent,
                "vkGetPhysicalDeviceFeatures2 unavailable");
    }
    get_features2(physical_device, features);
}

static vk_device_architecture get_device_architecture(const vk::PhysicalDevice& device) {
]=])

string(FIND "${_source}" "${_anchor}" _anchor_index)
if (_anchor_index EQUAL -1)
    message(FATAL_ERROR "llama Vulkan helper insertion point not found")
endif()
string(REPLACE "${_anchor}" "${_helper}" _source "${_source}")

set(_direct_calls
    "vkGetPhysicalDeviceFeatures2(device->physical_device, &device_features2);"
    "vkGetPhysicalDeviceFeatures2(physical_device, &device_features2);"
    "vkGetPhysicalDeviceFeatures2(vkdev, &device_features2);"
)
set(_dynamic_calls
    "ggml_vk_get_physical_device_features2(device->physical_device, &device_features2);"
    "ggml_vk_get_physical_device_features2(physical_device, &device_features2);"
    "ggml_vk_get_physical_device_features2(vkdev, &device_features2);"
)

list(LENGTH _direct_calls _count)
math(EXPR _last "${_count} - 1")
foreach(_index RANGE 0 ${_last})
    list(GET _direct_calls ${_index} _old)
    list(GET _dynamic_calls ${_index} _new)
    string(FIND "${_source}" "${_old}" _call_index)
    if (_call_index EQUAL -1)
        message(FATAL_ERROR "llama Vulkan direct feature query not found: ${_old}")
    endif()
    string(REPLACE "${_old}" "${_new}" _source "${_source}")
endforeach()

file(WRITE "${_llama_vulkan_source}" "${_source}")
