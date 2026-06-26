package me.tamkungz.hostlens;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HostLensSupportTest {

    @Test
    void cleanAndBlankShouldNormalizeText() {
        assertTrue(HostLensSupport.isBlank(null));
        assertTrue(HostLensSupport.isBlank("   "));
        assertFalse(HostLensSupport.isBlank("hostlens"));
        assertTrue(HostLensSupport.clean(null).isEmpty());
        assertTrue(HostLensSupport.clean("  value  ").equals("value"));
    }

    @Test
    void osFamilyShouldBeKnownValue() {
        String family = HostLensSupport.osFamily();

        assertNotNull(family);
        assertTrue(Set.of("windows", "linux", "macos", "unknown").contains(family));
    }

    @Test
    void hostNameShouldNeverBeBlank() {
        assertFalse(HostLensSupport.hostName().isBlank());
    }
}
