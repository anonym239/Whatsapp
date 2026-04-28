# AutoReply Bot – Alexander 🤖

Eine native Android-App, die automatisch auf WhatsApp-Nachrichten antwortet, wenn du nicht erreichbar bist.

## Features

- 🤖 **Automatische Antworten** via NotificationListenerService + Notification Reply API
- ⏱️ **Einstellbare Verzögerung** (1–60 Minuten)
- ✏️ **Anpassbare Nachricht** mit `[Name]`-Platzhalter
- 🚫 **Blacklist** – bestimmte Kontakte ausschließen
- 📋 **Verlauf** – alle gesendeten Auto-Antworten
- 🔔 **Push-Benachrichtigung** wenn Bot geantwortet hat
- 🔄 **Foreground Service** – läuft auch im Hintergrund
- 📱 **Boot-Receiver** – startet nach Neustart automatisch

## Unterstützte Apps

- WhatsApp
- WhatsApp Business
- Telegram
- Signal
- Instagram
- Facebook Messenger

## Technologie

- **Sprache:** Java
- **Min SDK:** Android 8.0 (API 26)
- **Target SDK:** Android 16 (API 36)
- **Keine externen APIs** – nur native Android-Funktionen

## Setup

1. App installieren
2. **Benachrichtigungs-Zugriff** erteilen (Einstellungen → Apps → Spezielle App-Zugriffe)
3. Bot einschalten
4. Verzögerung und Nachricht anpassen

## Berechtigungen

| Berechtigung | Zweck |
|---|---|
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Nachrichten lesen & antworten |
| `FOREGROUND_SERVICE` | Im Hintergrund laufen |
| `RECEIVE_BOOT_COMPLETED` | Nach Neustart starten |
| `READ_CONTACTS` | Kontakte für Blacklist |
| `POST_NOTIFICATIONS` | Bot-Bestätigungen anzeigen |

## Projektstruktur

```
app/src/main/java/com/alexander/autoreplybot/
├── MainActivity.java                    # Haupt-UI
├── AutoReplyNotificationService.java    # Kern-Service (NotificationListener)
├── BotForegroundService.java            # Foreground Service
├── BootReceiver.java                    # Boot-Receiver
├── BlacklistActivity.java               # Blacklist-Verwaltung
├── LogActivity.java                     # Antwort-Verlauf
├── AppPreferences.java                  # Einstellungen (SharedPreferences)
└── LogEntry.java                        # Datenmodell
```

## Lizenz

MIT License – Alexander's Bot 🤖
