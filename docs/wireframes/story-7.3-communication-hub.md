# Story 7.3: Speaker Communication Hub - Wireframe

**Story**: Epic 7, Story 3 - Speaker Communication Hub
**Screen**: Communication Center
**User Role**: Speaker
**Related FR**: FR5 (Speaker Management)

---

## 6. Speaker Communication Hub

```
┌─────────────────────────────────────────────────────────────────────────────────────┐
│ ← Back                    Communication Center                         [Compose]      │
├─────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                       │
│  ┌─── CONVERSATIONS ──────────────┬─── ACTIVE CHAT ─────────────────────────────────┐│
│  │                                │                                                  ││
│  │ [All] [Organizers] [Speakers]  │  Sally Organizer - Event Lead                   ││
│  │                                │  Spring Conference 2025                          ││
│  │ ┌──────────────────────────┐   │                                                  ││
│  │ │ 🔴 Sally O. - Organizer  │   │  ┌──────────────────────────────────────────────┐││
│  │ │ Abstract feedback         │   │  │ Sally: Hi Peter! I reviewed your abstract  │││
│  │ │ 2 new messages           │   │  │ and it looks great. Just two small points: │││
│  │ │                          │   │  │ 1. Can you mention specific tools?         │││
│  │ └──────────────────────────┘   │  │ 2. Add a customer success story?          │││
│  │                                │  │                          Yesterday, 14:30   │││
│  │ ┌──────────────────────────┐   │  ├──────────────────────────────────────────────┤││
│  │ │ Mark T. - Speaker Coord  │   │  │ You: Thanks Sally! I'll add Prometheus/    │││
│  │ │ Logistics update         │   │  │ Grafana details and our Swiss Re case.     │││
│  │ │ Read                      │   │  │                          Yesterday, 16:45   │││
│  │ └──────────────────────────┘   │  ├──────────────────────────────────────────────┤││
│  │                                │  │ Sally: Perfect! Also, would you be         │││
│  │ ┌──────────────────────────┐   │  │ interested in joining our panel discussion │││
│  │ │ Tech Support              │   │  │ after your talk? Topic: "K8s Future"       │││
│  │ │ AV requirements           │   │  │                            Today, 09:15     │││
│  │ │ Resolved ✓                │   │  ├──────────────────────────────────────────────┤││
│  │ └──────────────────────────┘   │  │ Sally: No pressure - just thought your     │││
│  │                                │  │ expertise would add great value!            │││
│  │ ┌──────────────────────────┐   │  │ [👍] [👎] [Reply]          Today, 09:16     │││
│  │ │ Group: All Speakers      │   │  └──────────────────────────────────────────────┘││
│  │ │ Event updates            │   │                                                  ││
│  │ │ 12 members               │   │  ┌──────────────────────────────────────────────┐││
│  │ └──────────────────────────┘   │  │ Type your message...                        │││
│  │                                │  │                                              │││
│  └────────────────────────────────┘  └──────────────────────────────────────────────┘││
│                                       [📎 Attach] [😊] [Send]                        ││
│                                                                                       │
│  ┌─── ANNOUNCEMENTS ────────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  📢 From: Event Team                                         Mar 28, 10:00      │ │
│  │  Subject: Agenda Published - Check Your Time Slots                              │ │
│  │  The preliminary agenda is now live. Please confirm your time slots...          │ │
│  │  [Read More]                                                                    │ │
│  │                                                                                  │ │
│  │  📢 From: Tech Support                                       Mar 25, 14:00      │ │
│  │  Subject: Presentation Template Available                                       │ │
│  │  Download the official BATbern PowerPoint template from...                      │ │
│  │  [Download Template]                                                            │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
│  ┌─── QUICK CONTACTS ───────────────────────────────────────────────────────────────┐ │
│  │                                                                                  │ │
│  │  Event Team:                    Technical Support:                              │ │
│  │  Sally O. (Lead)                📧 tech@batbern.ch                             │ │
│  │  📧 sally@batbern.ch           📱 Emergency: +41 79 XXX XXXX                   │ │
│  │  💬 Available now                                                               │ │
│  │                                 Venue Contact:                                  │ │
│  │  Mark T. (Speakers)             Kursaal Bern                                   │ │
│  │  📧 mark@batbern.ch            📱 +41 31 XXX XXXX                              │ │
│  │  💬 Away - back at 14:00                                                        │ │
│  └──────────────────────────────────────────────────────────────────────────────────┘ │
│                                                                                       │
└─────────────────────────────────────────────────────────────────────────────────────┘
```

---

## API Requirements

### Initial Page Load APIs

When the Communication Center screen loads, the following APIs are called to provide the necessary data:

1. **GET /api/v1/speakers/{speakerId}/conversations**
   - Query params: filter (all, organizers, speakers), limit (20), offset
   - Returns: List of conversations with participants, last message preview, unread count, timestamps, conversation types
   - Used for: Populate conversations list with all threads

2. **GET /api/v1/speakers/{speakerId}/conversations/{conversationId}/messages**
   - Query params: limit (50), beforeMessageId (for pagination)
   - Returns: Messages in conversation with content, timestamps, sender info, read status, reactions
   - Used for: Load active chat messages

3. **GET /api/v1/speakers/{speakerId}/conversations/{conversationId}/participants**
   - Returns: List of conversation participants with names, roles, availability status
   - Used for: Display conversation participants and their status

4. **GET /api/v1/speakers/{speakerId}/announcements**
   - Query params: limit (5), unreadOnly (false)
   - Returns: Announcements from event team with subjects, previews, timestamps, read status, action buttons
   - Used for: Populate announcements section

5. **GET /api/v1/events/{eventId}/contacts**
   - Returns: Event team contacts with names, roles, emails, availability status, phone numbers
   - Used for: Populate quick contacts section

6. **WebSocket /ws/speakers/{speakerId}/messages**
   - Real-time updates: New messages, typing indicators, read receipts, presence updates
   - Used for: Live message updates and presence information

---

## Action APIs

### Messaging & Conversations

1. **POST /api/v1/speakers/{speakerId}/conversations/{conversationId}/messages**
   - Payload: `{ content, attachments: [], replyToMessageId (optional) }`
   - Response: Message ID, sent timestamp, delivery status
   - Used for: Send message in conversation

2. **POST /api/v1/speakers/{speakerId}/conversations/create**
   - Payload: `{ participantIds: [], initialMessage, subject }`
   - Response: Conversation ID, creation confirmation
   - Used for: Start new conversation with organizers or speakers

3. **PUT /api/v1/speakers/{speakerId}/conversations/{conversationId}/messages/{messageId}**
   - Payload: `{ content }`
   - Response: Updated message, edit timestamp
   - Used for: Edit sent message (within time limit)

4. **DELETE /api/v1/speakers/{speakerId}/conversations/{conversationId}/messages/{messageId}**
   - Response: Deletion confirmation
   - Used for: Delete sent message (within time limit)

5. **POST /api/v1/speakers/{speakerId}/conversations/{conversationId}/messages/{messageId}/react**
   - Payload: `{ reactionType: "thumbs_up|thumbs_down|heart|laugh" }`
   - Response: Updated reactions count
   - Used for: React to message (emoji reactions)

6. **POST /api/v1/speakers/{speakerId}/conversations/{conversationId}/typing**
   - Payload: `{ isTyping: boolean }`
   - Response: Typing indicator broadcast confirmation
   - Used for: Send typing indicator to conversation participants

7. **PUT /api/v1/speakers/{speakerId}/conversations/{conversationId}/read**
   - Payload: `{ lastReadMessageId }`
   - Response: Read receipt confirmation
   - Used for: Mark messages as read up to specific message

### Attachments

8. **POST /api/v1/speakers/{speakerId}/conversations/{conversationId}/attachments/upload**
   - Payload: File upload (multipart/form-data)
   - Response: Attachment ID, URL, file metadata
   - Used for: Upload file attachment for message

9. **GET /api/v1/attachments/{attachmentId}/download**
   - Returns: Download URL, expiration timestamp
   - Used for: Download message attachment

### Conversation Management

10. **PUT /api/v1/speakers/{speakerId}/conversations/{conversationId}/archive**
    - Response: Archive confirmation
    - Used for: Archive conversation

11. **DELETE /api/v1/speakers/{speakerId}/conversations/{conversationId}**
    - Response: Deletion confirmation (deletes for user only)
    - Used for: Delete conversation from inbox

12. **PUT /api/v1/speakers/{speakerId}/conversations/{conversationId}/mute**
    - Payload: `{ muteDuration: "1hour|1day|forever" }`
    - Response: Mute confirmation
    - Used for: Mute conversation notifications

13. **GET /api/v1/speakers/{speakerId}/conversations/search**
    - Query params: query, participantId, dateRange
    - Returns: Matching conversations and messages
    - Used for: Search conversations and message content

### Announcements

14. **PUT /api/v1/speakers/{speakerId}/announcements/{announcementId}/read**
    - Response: Read status updated
    - Used for: Mark announcement as read

15. **GET /api/v1/speakers/{speakerId}/announcements/{announcementId}/details**
    - Returns: Full announcement content, attachments, action items
    - Used for: View full announcement details

16. **POST /api/v1/speakers/{speakerId}/announcements/{announcementId}/acknowledge**
    - Payload: `{ acknowledgeType: "read|action_taken|attending" }`
    - Response: Acknowledgment confirmation
    - Used for: Acknowledge announcement or action item

### Contacts & Availability

17. **GET /api/v1/speakers/{speakerId}/contacts/organizers**
    - Returns: All event organizers with contact info, roles, availability
    - Used for: View full organizer contact list

18. **GET /api/v1/speakers/{speakerId}/contacts/speakers**
    - Query params: eventId (optional)
    - Returns: Other speakers with contact info, expertise, connection status
    - Used for: View fellow speakers contact list

19. **POST /api/v1/speakers/{speakerId}/contacts/{contactId}/message**
    - Payload: `{ initialMessage }`
    - Response: Conversation ID, message sent confirmation
    - Used for: Start direct message with contact

### Notifications & Preferences

20. **GET /api/v1/speakers/{speakerId}/communication/preferences**
    - Returns: Notification preferences, message delivery settings, quiet hours
    - Used for: Load communication preferences

21. **PUT /api/v1/speakers/{speakerId}/communication/preferences**
    - Payload: `{ emailNotifications: boolean, pushNotifications: boolean, quietHours: { start, end }, messageSound: boolean }`
    - Response: Updated preferences
    - Used for: Update communication preferences

---

## Navigation Map

### Primary Navigation Actions

1. **← Back button** → Navigate back to `Speaker Dashboard`
   - Returns to main speaker dashboard

2. **[Compose] button** → Opens new conversation modal
   - Select recipients (organizers/speakers)
   - Enter subject and message
   - Can create group conversation
   - No screen navigation after send

3. **Filter tabs [All] [Organizers] [Speakers]** → Filters conversation list
   - Updates displayed conversations
   - Maintains selection
   - No screen navigation

4. **Conversation item click** → Loads conversation in active chat panel
   - Displays messages
   - Marks as read
   - Updates right panel
   - No screen navigation

5. **Participant name click (in chat header)** → Opens participant profile modal
   - Shows profile information
   - Contact details
   - Shared events
   - No screen navigation

6. **Message click (in conversation list)** → Loads conversation
   - Same as conversation item click
   - Scrolls to specific message if deep link

7. **Group conversation click** → Loads group chat
   - Shows all participants
   - Group message history
   - Updates active chat panel
   - No screen navigation

8. **Message reaction button ([👍] [👎])** → Adds reaction
   - Quick reaction to message
   - Updates reaction count
   - No screen navigation

9. **[Reply] button** → Focuses message input with context
   - Pre-fills reply reference
   - Focuses text input
   - No screen navigation

10. **Message input field** → Enables typing
    - Sends typing indicator
    - Character count
    - @ mention suggestions
    - No screen navigation

11. **[📎 Attach] button** → Opens file picker
    - Select files to attach
    - Upload progress shown
    - Preview attachments
    - No screen navigation

12. **[😊] emoji button** → Opens emoji picker
    - Select emoji
    - Inserts at cursor
    - No screen navigation

13. **[Send] button** → Sends message
    - Submits message
    - Clears input field
    - Updates chat immediately
    - No screen navigation

14. **Announcement [Read More] button** → Navigate to `Announcement Details Screen`
    - Full announcement content
    - Attachments
    - Related actions
    - Acknowledgment option

15. **Announcement [Download Template] button** → Triggers download
    - Downloads attached file
    - Marks announcement as read
    - No screen navigation

16. **Quick Contact email link** → Opens email client
    - Pre-fills recipient
    - External app
    - No app navigation

17. **Quick Contact [💬] button** → Opens conversation with contact
    - Starts or loads existing conversation
    - Updates active chat panel
    - No screen navigation

18. **Availability status indicator** → Shows status details tooltip
    - Status message
    - Expected return time
    - No navigation

### Secondary Navigation (Conversation Actions)

19. **Long press/right-click on message** → Opens message action menu
    - Edit message (if own, within time limit)
    - Delete message (if own, within time limit)
    - Copy message
    - Report message
    - No screen navigation

20. **Long press/right-click on conversation** → Opens conversation action menu
    - Mute conversation
    - Archive conversation
    - Delete conversation
    - Mark as unread
    - No screen navigation

21. **Attachment click (in message)** → Opens attachment viewer
    - In-app viewer for images/docs
    - Download option
    - Can navigate to full screen viewer

22. **@ mention in message** → Shows user profile tooltip
    - User info preview
    - Click to view full profile
    - Can start direct conversation

23. **Link in message** → Opens link
    - In-app browser for internal links
    - External browser for external links
    - Preview shown on hover

### Event-Driven Navigation

24. **New message received (WebSocket)** → Updates conversation list
    - Adds unread indicator
    - Moves conversation to top
    - Shows notification if inactive
    - No automatic navigation

25. **Message sent successfully** → Confirms delivery
    - Shows checkmark
    - Updates message status
    - No screen navigation

26. **Typing indicator received** → Shows "... is typing"
    - Appears in active chat
    - Real-time update
    - No screen navigation

27. **Message read by recipient** → Shows read receipt
    - Updates message status
    - Shows timestamp
    - No screen navigation

28. **New announcement posted** → Shows notification badge
    - Increments unread count
    - Highlights announcement section
    - No automatic navigation

29. **Organizer status changed to available** → Updates presence indicator
    - Changes status icon
    - Updates tooltip
    - No screen navigation

30. **File upload complete** → Shows attachment in message
    - Upload progress complete
    - Attachment preview shown
    - Ready to send
    - No screen navigation

31. **Message edited by sender** → Updates message content
    - Shows "edited" indicator
    - Updates timestamp
    - No screen navigation

32. **Message deleted by sender** → Removes or replaces message
    - Shows "message deleted" placeholder
    - Updates conversation
    - No screen navigation

33. **Conversation archived** → Removes from main list
    - Moves to archived folder
    - Shows undo option briefly
    - No screen navigation

34. **Connection lost** → Shows offline indicator
    - Disables send button
    - Queues messages locally
    - No screen navigation

35. **Connection restored** → Re-enables features
    - Sends queued messages
    - Syncs missed messages
    - Removes offline indicator
    - No screen navigation

---
