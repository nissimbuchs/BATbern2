package ch.batbern.shared.test;

import ch.batbern.testprobe.ClientErrorProbeController;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The client-error contract every service's {@code GlobalExceptionHandler} must meet (#1148,
 * #1157, #1103): a client mistake answers 4xx with severity LOW, never 500 CRITICAL.
 *
 * <p>Runs the given advice through the real {@code DispatcherServlet} resolver chain against a
 * probe controller, so a handler that only works when called directly does not pass. These are
 * the exact cases the weekly ZAP scan and production logs showed as 500:
 * {@code MissingServletRequestParameterException}, {@code MissingRequestHeaderException},
 * {@code HttpMediaTypeNotSupportedException}, an unknown path, a wrong verb, and a NUL byte that
 * PostgreSQL rejects with SQLState 22021.
 */
public final class ClientErrorContract {

    private static final String BASE = ClientErrorProbeController.BASE;

    private ClientErrorContract() {
    }

    public static void verify(Object controllerAdvice) throws Exception {
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new ClientErrorProbeController())
                .setControllerAdvice(controllerAdvice)
                .build();

        mvc.perform(get(BASE + "/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.severity").value("LOW"));

        mvc.perform(get(BASE + "/header"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.severity").value("LOW"));

        mvc.perform(post(BASE + "/json")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("a=b"))
                .andExpect(status().isUnsupportedMediaType());

        mvc.perform(get(BASE + "/does-not-exist"))
                .andExpect(status().isNotFound());

        mvc.perform(post(BASE + "/param"))
                .andExpect(status().isMethodNotAllowed());

        mvc.perform(get(BASE + "/nul"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.severity").value("LOW"));
    }
}
