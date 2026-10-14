package com.acme.salary.auth;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Creates demo users at startup so a reviewer can sign in right away: one per role, plus one regional officer
 * for each country that has employees. Assessment build only: turn it off with SEED_DEMO_USERS=false.
 * The password is public on the sign-in page.
 */
@Component
@ConditionalOnProperty(name = "app.seed.demo-users", havingValue = "true")
class DemoUserSeeder implements ApplicationRunner {

    static final String DEMO_PASSWORD = "Demo@12345";
    private static final Logger log = LoggerFactory.getLogger(DemoUserSeeder.class);

    /** The countries used by the employee seed script (scripts/seed-employees.js). */
    private static final Map<String, String> COUNTRIES = Map.of(
            "US", "United States", "IN", "India", "GB", "United Kingdom", "DE", "Germany", "CA", "Canada",
            "FR", "France", "AU", "Australia", "JP", "Japan", "SG", "Singapore", "BR", "Brazil");

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
        List<User> demoUsers = new ArrayList<>(List.of(
                demoUser("superadmin@acme.test", "Sam Superadmin", Role.SUPERADMIN, null),
                demoUser("manager@acme.test", "Maya Manager", Role.HR_MANAGER, null),
                demoUser("viewer@acme.test", "Vera Viewer", Role.HR_VIEWER, null)));

        // A regional officer per country, e.g. hr.in@acme.test can only see employees in India.
        COUNTRIES.forEach((code, name) -> demoUsers.add(
                demoUser("hr." + code.toLowerCase() + "@acme.test", "Regional HR (" + name + ")", Role.COUNTRY_HR, code)));

        for (User user : demoUsers) {
            if (users.findByEmailIgnoreCase(user.getEmail()).isEmpty()) {   // only create the missing ones
                users.save(user);
                log.info("Created demo user {} ({})", user.getEmail(), user.getRole());
            }
        }
    }

    private User demoUser(String email, String name, Role role, String countryScope) {
        return new User(email, passwordEncoder.encode(DEMO_PASSWORD), name, role, countryScope, clock.instant());
    }
}
