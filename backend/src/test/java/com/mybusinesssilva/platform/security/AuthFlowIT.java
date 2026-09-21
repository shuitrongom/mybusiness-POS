package com.mybusinesssilva.platform.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.mybusinesssilva.platform.security.auth.SuperAdminUserRepository;
import com.mybusinesssilva.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba de integración de la cadena HTTP de seguridad completa: verifica que, tras iniciar
 * sesión, el token de acceso autoriza los endpoints protegidos del Super Admin.
 *
 * <p>Cubre específicamente el escenario que un test unitario no detecta: que el filtro JWT
 * establezca la autenticación dentro de la cadena de Spring Security (regresión de doble
 * registro de filtro).
 */
class AuthFlowIT extends AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext context;
    @Autowired
    private SuperAdminUserRepository superAdminUserRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    void loginThenAccessProtectedEndpoint() throws Exception {
        // Asegura que exista un Super Admin (el seeder no corre en perfil de test).
        String email = "e2e-admin@mybusinesssilva.com";
        String password = "Test1234!";
        if (superAdminUserRepository.findByEmail(email).isEmpty()) {
            superAdminUserRepository.insert(email, passwordEncoder.encode(password), "E2E Admin");
        }

        MockMvc mvc = mockMvc();

        // 1) Login: obtiene el token de acceso.
        String loginBody = "{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}";
        String response = mvc.perform(post("/api/v1/auth/superadmin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        String token = json.get("accessToken").asString();
        assertThat(token).isNotBlank();

        // 2) Endpoint protegido SIN token: 401.
        mvc.perform(get("/api/v1/admin/plans"))
                .andExpect(status().isUnauthorized());

        // 3) Endpoint protegido CON token: 200 (autorización correcta en la cadena de seguridad).
        mvc.perform(get("/api/v1/admin/plans")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }
}
