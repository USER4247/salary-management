package com.acme.salary.auth.user;

import com.acme.salary.auth.ability.Role;
import java.time.Clock;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates one demo user per role so a reviewer can sign in. Only for the assessment build:
 * disable with {@code SEED_DEMO_USERS=false}. The passwords are public on the sign-in page by design.
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo-users", havingValue = "true")
class DemoUserSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "Demo@12345";
    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    DemoUserSeeder(UserRepository users, PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<User> demo = List.of(
                demoUser("admin@acme.test", "Alex Admin", Role.HR_ADMIN, null),
                demoUser("viewer@acme.test", "Vera Viewer", Role.HR_VIEWER, null),
                demoUser("india.hr@acme.test", "Indira HR", Role.COUNTRY_HR, "IN"));
        for (User user : demo) {
            if (users.findByEmailIgnoreCase(user.getEmail()).isEmpty()) {
                users.save(user);
                log.info("Seeded demo user {} ({})", user.getEmail(), user.getRole());
            }
        }
    }

    private User demoUser(String email, String name, Role role, String countryScope) {
        return new User(email, passwordEncoder.encode(DEMO_PASSWORD), name, role, countryScope, clock.instant());
    }
}
