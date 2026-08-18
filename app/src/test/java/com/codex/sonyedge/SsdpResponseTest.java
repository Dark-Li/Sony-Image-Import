package com.codex.sonyedge;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.junit.Test;

import java.net.InetAddress;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;

public class SsdpResponseTest {
    @Test
    public void parsesSonyResponseHeadersCaseInsensitively() throws Exception {
        String raw = "HTTP/1.1 200 OK\r\n"
                + "LOCATION: http://192.168.122.1:64321/dd.xml\r\n"
                + "Usn: uuid:camera::urn:schemas-upnp-org:service:ContentDirectory:1\r\n"
                + "st: urn:schemas-upnp-org:service:ContentDirectory:1\r\n"
                + "SERVER: Sony/1.0 UPnP/1.0\r\n"
                + "CACHE-CONTROL: max-age=1800\r\n\r\n";

        SsdpResponse response = SsdpResponse.parse(raw, InetAddress.getByName("192.168.122.1"));

        assertNotNull(response);
        assertEquals(200, response.statusCode);
        assertEquals("http://192.168.122.1:64321/dd.xml", response.location);
        assertEquals("urn:schemas-upnp-org:service:ContentDirectory:1", response.searchTarget);
        assertEquals("Sony/1.0 UPnP/1.0", response.server);
        assertEquals("192.168.122.1", response.sourceAddress);
    }

    @Test
    public void buildsLegacySonyDescriptionLocationsWithDdXmlFirst() {
        List<String> locations = DiscoveryClient.legacyDescriptionLocations(
                new LinkedHashSet<>(Arrays.asList("192.168.122.1", "0.0.0.0"))
        );

        assertEquals("http://192.168.122.1:64321/dd.xml", locations.get(0));
        assertEquals("http://192.168.122.1:64321/DmsDesc.xml", locations.get(1));
        org.junit.Assert.assertFalse(locations.toString().contains("0.0.0.0"));
    }
}
