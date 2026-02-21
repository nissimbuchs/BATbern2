/**
 * PrivacyPage Component
 * GDPR-compliant privacy policy for BATbern website and Watch app
 */

import { PublicLayout } from '@/components/public/PublicLayout';
import { Card, CardContent, CardHeader } from '@/components/public/ui/card';

const PrivacyPage = () => {
  const currentYear = new Date().getFullYear();

  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-4xl">
        {/* Hero Section */}
        <div className="mb-12">
          <h1 className="text-4xl md:text-5xl font-light mb-4 text-zinc-100">
            Datenschutzerklärung
          </h1>
          <p className="text-sm text-zinc-400">Letzte Aktualisierung: {currentYear}</p>
        </div>

        {/* Important Notice */}
        <Card className="bg-blue-900/20 border-blue-800 mb-8">
          <CardContent className="pt-6">
            <p className="text-zinc-100 font-medium">
              <strong>Wichtig:</strong> BATbern sammelt keine persönlichen Daten über unsere
              öffentliche Website oder die Watch App. Alle angezeigten Event-Informationen sind
              öffentlich und werden von Event-Organisatoren bereitgestellt.
            </p>
          </CardContent>
        </Card>

        {/* Overview */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">1. Überblick</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>
              Diese Datenschutzerklärung erklärt, wie die BATbern-Plattform (Website und Watch App)
              mit Daten umgeht. Ihr Datenschutz ist uns wichtig, und wir haben unsere Plattform so
              konzipiert, dass Ihre persönlichen Informationen geschützt werden.
            </p>
          </CardContent>
        </Card>

        {/* Data Collection */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">2. Datenerhebung</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <h3 className="text-xl font-medium text-zinc-100">2.1 Öffentliche Website</h3>
            <p>
              Unsere öffentliche Website <strong>sammelt keine persönlichen Daten</strong> ohne Ihre
              ausdrückliche Einwilligung:
            </p>
            <ul className="list-disc pl-6 space-y-2">
              <li>
                Keine automatische Erfassung von Namen, E-Mail-Adressen oder Kontaktinformationen
              </li>
              <li>Kein Standort-Tracking</li>
              <li>Keine Erstellung von Benutzerprofilen oder Accounts ohne Registrierung</li>
              <li>Keine Tracking-Cookies für Marketing-Zwecke</li>
            </ul>

            <h3 className="text-xl font-medium text-zinc-100 mt-6">2.2 Watch App</h3>
            <p>
              Die BATbern Watch App sammelt <strong>keinerlei persönliche Daten</strong>:
            </p>
            <ul className="list-disc pl-6 space-y-2">
              <li>Keine Erfassung von Namen, E-Mail-Adressen oder Kontaktinformationen</li>
              <li>Kein Standort-Tracking</li>
              <li>Keine Speicherung, welche Sessions Sie ansehen</li>
              <li>Keine Erstellung von Benutzerprofilen oder Accounts</li>
              <li>Keine Cookies oder Tracking-Technologien</li>
            </ul>

            <h3 className="text-xl font-medium text-zinc-100 mt-6">2.3 Event-Registrierung</h3>
            <p>Wenn Sie sich für ein Event über unsere Website registrieren, erfassen wir:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>Ihren Namen und Ihre E-Mail-Adresse (für Event-Kommunikation)</li>
              <li>Unternehmensname und Position (optional, für Netzwerk-Zwecke)</li>
              <li>Ihre Registrierungspräferenzen (z.B. Ernährungseinschränkungen)</li>
            </ul>
            <p className="text-sm text-zinc-400 mt-2">
              Diese Daten werden <strong>ausschließlich</strong> zur Event-Organisation verwendet
              und nach Ablauf der gesetzlichen Aufbewahrungsfristen gelöscht.
            </p>

            <h3 className="text-xl font-medium text-zinc-100 mt-6">2.4 Event-Informationen</h3>
            <p>
              Die Plattform zeigt öffentliche Event-Informationen an, die von den Berner Architekten
              Treffen Organisatoren bereitgestellt werden:
            </p>
            <ul className="list-disc pl-6 space-y-2">
              <li>Event-Titel, Datum und Ort</li>
              <li>Session-Zeitpläne und Beschreibungen</li>
              <li>Referentennamen und Unternehmensdetails</li>
              <li>Session-Abstracts und Präsentationsthemen</li>
            </ul>
            <p className="text-sm text-zinc-400 mt-2">
              Diese Informationen sind öffentlich verfügbar und werden ausschließlich zur Anzeige
              abgerufen.
            </p>
          </CardContent>
        </Card>

        {/* Local Data Storage */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">3. Lokale Datenspeicherung</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <h3 className="text-xl font-medium text-zinc-100">3.1 Website (Browser)</h3>
            <p>Die Website verwendet Browser-LocalStorage ausschließlich für:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>Sprachpräferenzen (Deutsch/Englisch)</li>
              <li>Technische Session-Daten für angemeldete Organisatoren</li>
            </ul>
            <p className="text-sm text-zinc-400 mt-2">
              Diese Daten verbleiben in Ihrem Browser und werden nie an unsere Server übertragen.
            </p>

            <h3 className="text-xl font-medium text-zinc-100 mt-6">3.2 Watch App (watchOS)</h3>
            <p>Die BATbern Watch App speichert Event-Informationen lokal auf Ihrer Apple Watch:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>Verbleiben ausschließlich auf Ihrem Gerät</li>
              <li>Werden nie an unsere Server oder Dritte übermittelt</li>
              <li>Werden automatisch gelöscht, wenn Sie die App deinstallieren</li>
              <li>
                Werden automatisch aktualisiert, wenn Ihre Watch mit dem Internet verbunden ist
              </li>
            </ul>
          </CardContent>
        </Card>

        {/* Network Communication */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">4. Netzwerk-Kommunikation</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>Website und Watch App kommunizieren mit unseren Servern, um:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>Aktuelle Event-Informationen abzurufen</li>
              <li>Zeitplan-Updates zu prüfen</li>
              <li>Event-Registrierungen zu verarbeiten (nur Website)</li>
            </ul>
            <p className="mt-4">Diese Kommunikationen:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>
                Enthalten keine persönlichen Informationen (außer bei expliziter Registrierung)
              </li>
              <li>Sind mit HTTPS verschlüsselt</li>
              <li>Erstellen keine benutzerspezifischen Logs</li>
            </ul>
          </CardContent>
        </Card>

        {/* Third-Party Services */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">5. Drittanbieter-Dienste</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>
              BATbern verwendet <strong>keine</strong> Drittanbieter-Analyse-, Werbe- oder
              Tracking-Dienste auf der öffentlichen Website oder der Watch App.
            </p>
            <p className="text-sm text-zinc-400 mt-2">
              Für angemeldete Organisatoren nutzen wir AWS Cognito für sichere Authentifizierung.
              Diese Daten sind durch AWS-Datenschutzrichtlinien geschützt.
            </p>
          </CardContent>
        </Card>

        {/* GDPR Rights */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">6. Ihre Rechte (DSGVO)</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>
              Gemäß der Datenschutz-Grundverordnung (DSGVO) und dem Schweizer Datenschutzgesetz
              haben Sie folgende Rechte:
            </p>
            <ul className="list-disc pl-6 space-y-2">
              <li>
                <strong>Recht auf Auskunft:</strong> Sie können Informationen darüber anfordern,
                welche Daten wir verarbeiten
              </li>
              <li>
                <strong>Recht auf Zugang:</strong> Sie können eine Kopie Ihrer Daten anfordern
              </li>
              <li>
                <strong>Recht auf Berichtigung:</strong> Sie können Korrekturen Ihrer Daten
                anfordern
              </li>
              <li>
                <strong>Recht auf Löschung:</strong> Sie können die Löschung Ihrer Daten anfordern
              </li>
              <li>
                <strong>Recht auf Datenübertragbarkeit:</strong> Sie können Ihre Daten in einem
                maschinenlesbaren Format anfordern
              </li>
              <li>
                <strong>Widerspruchsrecht:</strong> Sie können der Datenverarbeitung widersprechen
              </li>
            </ul>
            <p className="text-sm text-zinc-400 mt-4">
              <strong>Hinweis:</strong> Da BATbern auf der öffentlichen Website und Watch App keine
              persönlichen Daten ohne Ihre Einwilligung sammelt, sind die meisten dieser Rechte
              nicht anwendbar. Bei Fragen kontaktieren Sie uns bitte.
            </p>
          </CardContent>
        </Card>

        {/* Data Security */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">7. Datensicherheit</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>
              Obwohl wir auf der öffentlichen Plattform keine persönlichen Daten ohne Einwilligung
              sammeln, nehmen wir Sicherheit ernst:
            </p>
            <ul className="list-disc pl-6 space-y-2">
              <li>Alle Netzwerk-Kommunikationen verwenden HTTPS-Verschlüsselung</li>
              <li>Lokale Daten sind durch watchOS und Browser-Sicherheitsfeatures geschützt</li>
              <li>Wir befolgen Best Practices der Branche für App-Entwicklung</li>
              <li>Event-Registrierungsdaten werden auf sicheren AWS-Servern gespeichert</li>
            </ul>
          </CardContent>
        </Card>

        {/* Legal Basis */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">
              8. Rechtsgrundlage für die Verarbeitung
            </h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>Gemäß DSGVO Artikel 6 ist die Rechtsgrundlage für die Verarbeitung:</p>
            <ul className="list-disc pl-6 space-y-2">
              <li>
                <strong>Berechtigtes Interesse (Art. 6(1)(f) DSGVO):</strong> Bereitstellung von
                öffentlichen Event-Zeitplänen für Teilnehmer
              </li>
              <li>
                <strong>Einwilligung (Art. 6(1)(a) DSGVO):</strong> Event-Registrierung und
                Organizer-Accounts
              </li>
            </ul>
          </CardContent>
        </Card>

        {/* Contact & DPO */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">9. Datenschutzbeauftragter</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>
              Für Fragen zum Datenschutz können Sie unseren Datenschutzbeauftragten kontaktieren:
            </p>
            <div className="mt-4 p-4 bg-zinc-800/50 rounded-lg">
              <p className="font-medium">Verein Berner Architekten Treffen</p>
              <p className="mt-2">
                Email:{' '}
                <a
                  href="mailto:info@berner-architekten-treffen.ch"
                  className="text-blue-400 hover:text-blue-300"
                >
                  info@berner-architekten-treffen.ch
                </a>
              </p>
              <p className="mt-1">
                Website:{' '}
                <a href="https://batbern.ch" className="text-blue-400 hover:text-blue-300">
                  batbern.ch
                </a>
              </p>
            </div>
          </CardContent>
        </Card>

        {/* Supervisory Authority */}
        <Card className="bg-zinc-900/50 border-zinc-800 mb-8">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">10. Aufsichtsbehörde</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>
              Wenn Sie Bedenken bezüglich unseres Umgangs mit Daten haben, haben Sie das Recht, eine
              Beschwerde bei der zuständigen Datenschutz-Aufsichtsbehörde einzureichen:
            </p>
            <div className="mt-4 p-4 bg-zinc-800/50 rounded-lg">
              <p className="font-medium">
                Eidgenössischer Datenschutz- und Öffentlichkeitsbeauftragter (EDÖB)
              </p>
              <p className="mt-2">Feldeggweg 1</p>
              <p>3003 Bern, Schweiz</p>
              <p className="mt-2">
                Website:{' '}
                <a
                  href="https://www.edoeb.admin.ch"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-blue-400 hover:text-blue-300"
                >
                  www.edoeb.admin.ch
                </a>
              </p>
            </div>
          </CardContent>
        </Card>

        {/* Changes to Policy */}
        <Card className="bg-zinc-900/50 border-zinc-800">
          <CardHeader>
            <h2 className="text-2xl font-light text-zinc-100">11. Änderungen dieser Richtlinie</h2>
          </CardHeader>
          <CardContent className="space-y-4 text-zinc-300">
            <p>
              Wir können diese Datenschutzerklärung von Zeit zu Zeit aktualisieren. Änderungen
              werden auf dieser Seite veröffentlicht. Das Datum „Letzte Aktualisierung" oben gibt
              an, wann die Richtlinie zuletzt überarbeitet wurde.
            </p>
          </CardContent>
        </Card>
      </div>
    </PublicLayout>
  );
};

export default PrivacyPage;
