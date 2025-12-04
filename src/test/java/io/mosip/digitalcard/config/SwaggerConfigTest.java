package io.mosip.digitalcard.config;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;

@RunWith(MockitoJUnitRunner.class)
public class SwaggerConfigTest {

    @InjectMocks
    private SwaggerConfig swaggerConfig;

    @Mock
    private OpenApiProperties openApiProperties;

    @Mock
    private InfoProperty infoProperty;

    @Mock
    private LicenseProperty licenseProperty;

    @Mock
    private Service service;

    @Mock
    private Group group;

    @Test
    public void testOpenApi() {

        when(openApiProperties.getInfo()).thenReturn(infoProperty);
        when(infoProperty.getTitle()).thenReturn("Test API");
        when(infoProperty.getVersion()).thenReturn("1.0.0");
        when(infoProperty.getDescription()).thenReturn("Test Description");
        when(infoProperty.getLicense()).thenReturn(licenseProperty);
        when(licenseProperty.getName()).thenReturn("Test License");
        when(licenseProperty.getUrl()).thenReturn("http://test.license");

        Server server1 = new Server();
        server1.setDescription("Test Server 1");
        server1.setUrl("http://test1.com");
        Server server2 = new Server();
        server2.setDescription("Test Server 2");
        server2.setUrl("http://test2.com");
        List<Server> servers = Arrays.asList(server1, server2);
        when(openApiProperties.getService()).thenReturn(service);
        when(service.getServers()).thenReturn(servers);

        OpenAPI openAPI = swaggerConfig.openApi();

        assertNotNull(openAPI);
        Info info = openAPI.getInfo();
        assertEquals("Test API", info.getTitle());
        assertEquals("1.0.0", info.getVersion());
        assertEquals("Test Description", info.getDescription());
        License license = info.getLicense();
        assertEquals("Test License", license.getName());
        assertEquals("http://test.license", license.getUrl());

        assertEquals(2, openAPI.getServers().size());
        assertEquals("Test Server 1", openAPI.getServers().get(0).getDescription());
        assertEquals("http://test1.com", openAPI.getServers().get(0).getUrl());
        assertEquals("Test Server 2", openAPI.getServers().get(1).getDescription());
        assertEquals("http://test2.com", openAPI.getServers().get(1).getUrl());

    }

    @Test
    public void testGroupedOpenApi() {

        when(openApiProperties.getGroup()).thenReturn(group);
        when(group.getName()).thenReturn("test-group");
        List<String> paths = Arrays.asList("/api/v1/**", "/api/v2/**");
        when(group.getPaths()).thenReturn(paths);

        GroupedOpenApi groupedOpenApi = swaggerConfig.groupedOpenApi();

        assertNotNull(groupedOpenApi);
        assertEquals("test-group", groupedOpenApi.getGroup());
    }
}
