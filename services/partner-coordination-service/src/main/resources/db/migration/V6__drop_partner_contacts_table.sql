-- V6: Remove partner_contacts table
-- Partner contacts are now derived directly from the User Service:
-- any user with role=PARTNER and companyId matching a partner company
-- is automatically a contact of that company.
-- No explicit join table is needed.

DROP TABLE IF EXISTS partner_contacts;
