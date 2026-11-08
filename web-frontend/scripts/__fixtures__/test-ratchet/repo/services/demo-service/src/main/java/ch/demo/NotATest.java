package ch.demo;

// Main code is never scanned.
@org.springframework.transaction.annotation.Transactional
class NotATestIntegrationTest {
    @MockitoBean
    private UserApiClient userApiClient;
}
