package com.eminus.client.gpu.game;

import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.util.OptionalLong;

import com.eminus.Eminus;
import com.eminus.client.gpu.opengl.OpenGlLimits;

import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTMemoryBudget;
import org.lwjgl.vulkan.VK10;
import org.lwjgl.vulkan.VK11;
import org.lwjgl.vulkan.VkExtensionProperties;
import org.lwjgl.vulkan.VkMemoryHeap;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryBudgetPropertiesEXT;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties;
import org.lwjgl.vulkan.VkPhysicalDeviceMemoryProperties2;
import org.lwjgl.vulkan.VkPhysicalDeviceProperties;

record DeviceReading(OptionalLong texelElements, OptionalLong freeBytes) {
    private static final String OPENGL = "OpenGL";
    private static final String VULKAN = "Vulkan";
    private static final DeviceReading UNREAD = new DeviceReading(OptionalLong.empty(), OptionalLong.empty());
    private static final String TEXEL_LIMIT = "texel-buffer limit";
    private static final String FREE_MEMORY = "free video memory";
    private static final String PHYSICAL_DEVICE = "physical device";
    private static final String BACKEND_FIELD = "backend";
    private static final int NO_HEAP = -1;

    static DeviceReading read(GpuDevice device) {
        String backend = device.getDeviceInfo().backendName();

        return switch (backend) {
            case OPENGL -> new DeviceReading(OpenGlLimits.texelElements(), OpenGlLimits.freeBytes());
            case VULKAN -> vulkan(device);
            default -> {
                Eminus.LOGGER.warn("Device limits not read: backend {} is neither {} nor {}", backend, OPENGL, VULKAN);
                yield UNREAD;
            }
        };
    }

    private static DeviceReading vulkan(GpuDevice device) {
        VkPhysicalDevice physical;
        try {
            physical = vkPhysicalDevice(device);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError refused) {
            logRefusal(VULKAN, PHYSICAL_DEVICE, refused);
            return UNREAD;
        }

        return new DeviceReading(reading(VULKAN, TEXEL_LIMIT, () -> vkTexelElements(physical)),
                reading(VULKAN, FREE_MEMORY, () -> vkFreeBytes(physical)));
    }

    private static OptionalLong reading(String backend, String name, Query query) {
        try {
            return query.read();
        } catch (RuntimeException | LinkageError refused) {
            logRefusal(backend, name, refused);
            return OptionalLong.empty();
        }
    }

    private static void logRefusal(String backend, String name, Throwable refused) {
        Eminus.LOGGER.warn("The {} was not read on {}: {}", name, backend, refused.toString());
    }

    private static VkPhysicalDevice vkPhysicalDevice(GpuDevice device) throws ReflectiveOperationException {
        Field backend = FrontendGpuDevice.class.getDeclaredField(BACKEND_FIELD);
        backend.setAccessible(true);
        return ((VulkanDevice) backend.get(device)).vkDevice().getPhysicalDevice();
    }

    private static OptionalLong vkTexelElements(VkPhysicalDevice physical) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceProperties properties = VkPhysicalDeviceProperties.calloc(stack);
            VK10.vkGetPhysicalDeviceProperties(physical, properties);
            return OptionalLong.of(Integer.toUnsignedLong(properties.limits().maxTexelBufferElements()));
        }
    }

    private static OptionalLong vkFreeBytes(VkPhysicalDevice physical) {
        if (!supportsMemoryBudget(physical)) {
            return OptionalLong.empty();
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceMemoryBudgetPropertiesEXT budget =
                    VkPhysicalDeviceMemoryBudgetPropertiesEXT.calloc(stack).sType$Default();
            VkPhysicalDeviceMemoryProperties2 properties =
                    VkPhysicalDeviceMemoryProperties2.calloc(stack).sType$Default().pNext(budget);
            VK11.vkGetPhysicalDeviceMemoryProperties2(physical, properties);

            int heap = largestDeviceLocalHeap(properties.memoryProperties());
            return heap == NO_HEAP ? OptionalLong.empty()
                    : OptionalLong.of(Math.max(0L, budget.heapBudget(heap) - budget.heapUsage(heap)));
        }
    }

    private static int largestDeviceLocalHeap(VkPhysicalDeviceMemoryProperties memory) {
        int largest = NO_HEAP;
        long largestSize = 0L;
        for (int heap = 0; heap < memory.memoryHeapCount(); heap++) {
            VkMemoryHeap candidate = memory.memoryHeaps(heap);
            if ((candidate.flags() & VK10.VK_MEMORY_HEAP_DEVICE_LOCAL_BIT) != 0 && candidate.size() > largestSize) {
                largest = heap;
                largestSize = candidate.size();
            }
        }
        return largest;
    }

    private static boolean supportsMemoryBudget(VkPhysicalDevice physical) {
        int count;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer counted = stack.mallocInt(1);
            VK10.vkEnumerateDeviceExtensionProperties(physical, (String) null, counted, null);
            count = counted.get(0);
        }

        try (MemoryStack stack = MemoryStack.stackPush();
                VkExtensionProperties.Buffer extensions = VkExtensionProperties.malloc(count)) {
            IntBuffer counted = stack.ints(count);
            VK10.vkEnumerateDeviceExtensionProperties(physical, (String) null, counted, extensions);

            for (VkExtensionProperties extension : extensions) {
                if (EXTMemoryBudget.VK_EXT_MEMORY_BUDGET_EXTENSION_NAME.equals(extension.extensionNameString())) {
                    return true;
                }
            }
            return false;
        }
    }

    @FunctionalInterface
    private interface Query {
        OptionalLong read();
    }
}
