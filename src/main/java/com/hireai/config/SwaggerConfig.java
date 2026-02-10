package com.hireai.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HireAI — AI-Powered Hiring Platform")
                        .description("""
                                Intelligent hiring platform that leverages AI for resume parsing, \
                                candidate screening, job matching, and automated interviews. \
                                Built with Spring Boot, Spring AI (OpenAI), PostgreSQL with pgvector, \
                                RabbitMQ, and Redis.""")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("HireAI Team")
                                .email("team@hireai.dev")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Enter your JWT token. Obtain one via POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
                .tags(List.of(
                        new Tag().name("Authentication").description("Register, login, and token refresh"),
                        new Tag().name("Jobs").description("Create, search, update, and close job postings"),
                        new Tag().name("Candidates").description("Candidate profile management"),
                        new Tag().name("Resumes").description("Upload resumes (PDF/DOCX) — AI parses them asynchronously"),
                        new Tag().name("Applications").description("Apply to jobs, track status, recruiter pipeline management"),
                        new Tag().name("Interviews").description("AI-generated interview questions with real-time evaluation"),
                        new Tag().name("Matching").description("Semantic job matching using pgvector cosine similarity + AI explanations"),
                        new Tag().name("Dashboard").description("Recruiter pipeline view and candidate status dashboard")
                ));
    }
}
