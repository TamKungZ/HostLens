package me.tamkungz.hostlens;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

final class MemoryInspector implements HostInspector {

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public boolean supports(HostCaptureContext context) {
        return context.includeMemory();
    }

    @Override
    public void inspect(HostCaptureContext context, HostSnapshot.Builder snapshot) {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();

        long physicalTotal = -1L;
        long physicalFree = -1L;
        long swapTotal = -1L;
        long swapFree = -1L;

        java.lang.management.OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean) {
            physicalTotal = sunOsBean.getTotalMemorySize();
            physicalFree = sunOsBean.getFreeMemorySize();
            swapTotal = sunOsBean.getTotalSwapSpaceSize();
            swapFree = sunOsBean.getFreeSwapSpaceSize();
        }

        snapshot.memory(new MemoryInfo(
                heap.getUsed(),
                heap.getCommitted(),
                heap.getMax(),
                nonHeap.getUsed(),
                nonHeap.getCommitted(),
                physicalTotal,
                physicalFree,
                swapTotal,
                swapFree
        ));
    }
}
