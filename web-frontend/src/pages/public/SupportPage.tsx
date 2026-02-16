/**
 * SupportPage Component
 * Support and FAQ for BATbern website and Watch app
 */

import { PublicLayout } from '@/components/public/PublicLayout';
import { Card, CardContent, CardHeader } from '@/components/public/ui/card';
import { Mail, ExternalLink } from 'lucide-react';

const SupportPage = () => {
  return (
    <PublicLayout>
      <div className="container mx-auto px-4 py-12 max-w-4xl">
        {/* Hero Section */}
        <div className="mb-12">
          <h1 className="text-4xl md:text-5xl font-light mb-4 text-zinc-100">Support</h1>
          <p className="text-lg text-zinc-300">
            Hilfe und häufig gestellte Fragen zur BATbern-Plattform
          </p>
        </div>

        {/* FAQ Sections */}
        <div className="space-y-8">
          {/* General Questions */}
          <Card className="bg-zinc-900/50 border-zinc-800">
            <CardHeader>
              <h2 className="text-2xl font-light text-zinc-100">Allgemeine Fragen</h2>
            </CardHeader>
            <CardContent className="space-y-6 text-zinc-300">
              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">Was ist BATbern?</h3>
                <p className="leading-relaxed">
                  BATbern (Berner Architekten Treffen) ist eine Plattform für Architektur-Events in
                  Bern, Schweiz. Unsere Website bietet Informationen zu aktuellen und vergangenen
                  Events, Event-Registrierung und Zugang zu Präsentationsmaterialien. Unsere Watch
                  App ermöglicht schnellen Zugriff auf Event-Zeitpläne direkt von Ihrer Apple Watch.
                </p>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Welche Dienste bietet BATbern?
                </h3>
                <ul className="list-disc pl-6 space-y-2">
                  <li>Öffentliche Website mit Event-Informationen und Archiv</li>
                  <li>Event-Registrierung für Teilnehmer</li>
                  <li>Apple Watch App für Event-Zeitpläne</li>
                  <li>Speaker-Portal für Referenten</li>
                  <li>Partner-Portal für Event-Sponsoren</li>
                </ul>
              </div>
            </CardContent>
          </Card>

          {/* Website Support */}
          <Card className="bg-zinc-900/50 border-zinc-800">
            <CardHeader>
              <h2 className="text-2xl font-light text-zinc-100">Website-Support</h2>
            </CardHeader>
            <CardContent className="space-y-6 text-zinc-300">
              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Wie registriere ich mich für ein Event?
                </h3>
                <ol className="list-decimal pl-6 space-y-2">
                  <li>Besuchen Sie die Startseite und wählen Sie das aktuelle Event</li>
                  <li>Klicken Sie auf „Registrieren"</li>
                  <li>Füllen Sie das Registrierungsformular aus</li>
                  <li>Bestätigen Sie Ihre Registrierung per E-Mail</li>
                </ol>
                <p className="text-sm text-zinc-400 mt-2">
                  Sie erhalten einen QR-Code für den Check-in am Event-Tag.
                </p>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Kann ich meine Registrierung stornieren?
                </h3>
                <p className="leading-relaxed">
                  Ja, verwenden Sie den Link in Ihrer Bestätigungs-E-Mail, um Ihre Registrierung zu
                  stornieren. Bitte stornieren Sie frühzeitig, damit andere Teilnehmer Ihren Platz
                  nutzen können.
                </p>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Wie greife ich auf vergangene Events zu?
                </h3>
                <p className="leading-relaxed">
                  Besuchen Sie unsere{' '}
                  <a href="/archive" className="text-blue-400 hover:text-blue-300">
                    Archiv-Seite
                  </a>
                  , um alle vergangenen Events zu durchsuchen. Sie können Präsentationen,
                  Speaker-Profile und Event-Zusammenfassungen einsehen.
                </p>
              </div>
            </CardContent>
          </Card>

          {/* Watch App Support */}
          <Card className="bg-zinc-900/50 border-zinc-800">
            <CardHeader>
              <h2 className="text-2xl font-light text-zinc-100">BATbern Watch App</h2>
            </CardHeader>
            <CardContent className="space-y-6 text-zinc-300">
              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Welche Apple Watch Modelle werden unterstützt?
                </h3>
                <ul className="list-disc pl-6 space-y-2">
                  <li>Apple Watch Series 6 oder neuer</li>
                  <li>Apple Watch SE (1. und 2. Generation)</li>
                  <li>Apple Watch Ultra (1. und 2. Generation)</li>
                  <li>watchOS 10.0 oder neuer erforderlich</li>
                </ul>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Wie installiere ich die Watch App?
                </h3>
                <ol className="list-decimal pl-6 space-y-2">
                  <li>Öffnen Sie die Watch App auf Ihrem iPhone</li>
                  <li>Scrollen Sie zu „Verfügbare Apps"</li>
                  <li>Suchen Sie „BATbern Watch"</li>
                  <li>Tippen Sie auf „Installieren"</li>
                </ol>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Funktioniert die App offline?
                </h3>
                <p className="leading-relaxed">
                  Ja! Die Watch App speichert alle Event-Daten lokal auf Ihrer Apple Watch. Nach dem
                  ersten Download können Sie Event-Zeitpläne auch ohne Internetverbindung ansehen.
                  Die App aktualisiert automatisch alle 5 Minuten, wenn eine Verbindung verfügbar
                  ist.
                </p>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Die App zeigt keine Daten an
                </h3>
                <ol className="list-decimal pl-6 space-y-2">
                  <li>Überprüfen Sie Ihre Internetverbindung</li>
                  <li>Stellen Sie sicher, dass Ihre Watch mit dem iPhone verbunden ist</li>
                  <li>Öffnen Sie die App erneut – Daten werden automatisch geladen</li>
                  <li>Bei anhaltendem Problem: App deinstallieren und neu installieren</li>
                </ol>
              </div>
            </CardContent>
          </Card>

          {/* Privacy & Security */}
          <Card className="bg-zinc-900/50 border-zinc-800">
            <CardHeader>
              <h2 className="text-2xl font-light text-zinc-100">Datenschutz & Sicherheit</h2>
            </CardHeader>
            <CardContent className="space-y-6 text-zinc-300">
              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Welche Daten sammelt BATbern?
                </h3>
                <p className="leading-relaxed">
                  BATbern sammelt <strong>keine persönlichen Daten</strong> ohne Ihre Einwilligung:
                </p>
                <ul className="list-disc pl-6 space-y-2 mt-2">
                  <li>Öffentliche Website: Keine automatische Datenerfassung</li>
                  <li>Watch App: Keine persönlichen Daten</li>
                  <li>
                    Event-Registrierung: Nur Name und E-Mail (mit Ihrer ausdrücklichen Einwilligung)
                  </li>
                </ul>
                <p className="mt-4">
                  Weitere Details finden Sie in unserer{' '}
                  <a href="/privacy" className="text-blue-400 hover:text-blue-300">
                    Datenschutzerklärung
                  </a>
                  .
                </p>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Wo werden Event-Daten gespeichert?
                </h3>
                <ul className="list-disc pl-6 space-y-2">
                  <li>
                    <strong>Website:</strong> Event-Informationen werden temporär im Browser-Cache
                    gespeichert
                  </li>
                  <li>
                    <strong>Watch App:</strong> Event-Daten werden ausschließlich lokal auf Ihrer
                    Apple Watch gespeichert und niemals an unsere Server übertragen
                  </li>
                  <li>
                    <strong>Registrierungen:</strong> Auf sicheren AWS-Servern in der EU gespeichert
                  </li>
                </ul>
              </div>
            </CardContent>
          </Card>

          {/* Technical Requirements */}
          <Card className="bg-zinc-900/50 border-zinc-800">
            <CardHeader>
              <h2 className="text-2xl font-light text-zinc-100">Technische Anforderungen</h2>
            </CardHeader>
            <CardContent className="space-y-6 text-zinc-300">
              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">Website</h3>
                <ul className="list-disc pl-6 space-y-2">
                  <li>Moderne Browser: Chrome, Firefox, Safari, Edge (jeweils aktuelle Version)</li>
                  <li>JavaScript aktiviert</li>
                  <li>Internetverbindung erforderlich</li>
                </ul>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">Watch App</h3>
                <ul className="list-disc pl-6 space-y-2">
                  <li>Apple Watch Series 6 oder neuer</li>
                  <li>watchOS 10.0 oder neuer</li>
                  <li>iPhone mit iOS 17.0 oder neuer (für Installation)</li>
                  <li>WiFi-Verbindung für Daten-Updates (optional für Offline-Nutzung)</li>
                </ul>
              </div>
            </CardContent>
          </Card>

          {/* Contact Support */}
          <Card className="bg-zinc-900/50 border-zinc-800">
            <CardHeader>
              <h2 className="text-2xl font-light text-zinc-100">Kontakt & Support</h2>
            </CardHeader>
            <CardContent className="space-y-6 text-zinc-300">
              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Support-Team kontaktieren
                </h3>
                <p className="leading-relaxed mb-4">
                  Wenn Sie weitere Fragen haben oder Hilfe benötigen, kontaktieren Sie unser
                  Support-Team:
                </p>
                <div className="p-4 bg-zinc-800/50 rounded-lg space-y-3">
                  <div className="flex items-center gap-3">
                    <Mail className="h-5 w-5 text-blue-400" />
                    <a
                      href="mailto:info@berner-architekten-treffen.ch"
                      className="text-blue-400 hover:text-blue-300"
                    >
                      info@berner-architekten-treffen.ch
                    </a>
                  </div>
                  <div className="flex items-center gap-3">
                    <ExternalLink className="h-5 w-5 text-blue-400" />
                    <a href="https://batbern.ch" className="text-blue-400 hover:text-blue-300">
                      batbern.ch
                    </a>
                  </div>
                </div>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">Reaktionszeit</h3>
                <ul className="list-disc pl-6 space-y-2">
                  <li>Werktags (Mo–Fr): Best-effort</li>
                  <li>Wochenende: Best-effort</li>
                  <li>Während Events: Sofort-Support verfügbar</li>
                </ul>
              </div>

              <div>
                <h3 className="text-lg font-medium text-zinc-100 mb-2">
                  Feedback & Feature-Wünsche
                </h3>
                <p className="leading-relaxed">
                  Wir freuen uns über Ihr Feedback! Teilen Sie uns Ihre Ideen und
                  Verbesserungsvorschläge mit. Senden Sie eine E-Mail an{' '}
                  <a
                    href="mailto:info@berner-architekten-treffen.ch"
                    className="text-blue-400 hover:text-blue-300"
                  >
                    info@berner-architekten-treffen.ch
                  </a>{' '}
                  mit dem Betreff „Feedback".
                </p>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </PublicLayout>
  );
};

export default SupportPage;
