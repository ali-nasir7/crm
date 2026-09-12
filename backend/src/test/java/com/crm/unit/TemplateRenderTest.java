package com.crm.unit;

import com.crm.modules.email.service.EmailService;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateRenderTest {

    @Test
    void rendersKnownAndCustomVariables() {
        Map<String, Object> vars = new HashMap<>();
        vars.put("firstName", "John");
        vars.put("companyName", "ABC Clinic");
        vars.put("service", "IV Therapy");
        String out = EmailService.render("Hi {{firstName}}, does {{companyName}} offer {{service}}? Missing: {{nope}}", vars);
        assertThat(out).isEqualTo("Hi John, does ABC Clinic offer IV Therapy? Missing: ");
    }

    @Test
    void nullInputStaysNull() {
        assertThat(EmailService.render(null, Map.of())).isNull();
    }
}
