package ch.demo.service;

/** Not a client package: a public path here (a redirect, a link in a mail) is not an S2S call. */
public class DemoService {
    String link() {
        return "https://www.batbern.ch/api/v1/events";
    }
}
