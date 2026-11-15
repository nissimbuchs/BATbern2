package ch.batbern.events.architecture.fixtures.registrationcontroller;

/** Breaks the #1315 rule: writes through the repository instead of its service. */
public class RegistrationController {
    private final RegistrationRepository registrationRepository;

    public RegistrationController(RegistrationRepository registrationRepository) {
        this.registrationRepository = registrationRepository;
    }

    public void delete(Object registration) {
        registrationRepository.delete(registration);
    }
}
