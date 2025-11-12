package ch.batbern.partners.domain;

/**
 * Contact role for partner contacts.
 *
 * Defines the type of relationship a contact has with the partner organization.
 */
public enum ContactRole {
    /**
     * Primary contact - main point of contact for the partnership
     */
    PRIMARY,

    /**
     * Billing contact - handles invoicing and financial matters
     */
    BILLING,

    /**
     * Technical contact - handles technical integration and support
     */
    TECHNICAL,

    /**
     * Marketing contact - handles marketing materials and communications
     */
    MARKETING
}
