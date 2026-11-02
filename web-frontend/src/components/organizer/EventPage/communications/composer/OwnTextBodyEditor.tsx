/**
 * The own-text body editor (Story 16.5): TinyMCE with the setup of the admin template editor
 * (`Admin/EmailTemplateEditModal.tsx`: same plugins, toolbar and self-hosted script). Loaded lazily
 * by `OwnTextEditor`, so the editor is only fetched when an organizer switches to own text.
 *
 * Uncontrolled on purpose: a controlled TinyMCE re-renders on every keystroke and the cursor jumps.
 * The parent remounts it (new `key`) when the content is replaced, e.g. by "use as starting point".
 */
import React from 'react';
import { Editor as TinyMCEEditor } from '@tinymce/tinymce-react';
import type { Editor as TinyEditor } from 'tinymce';

export interface BodyEditorHandle {
  insertContent: (text: string) => void;
}

interface OwnTextBodyEditorProps {
  initialValue: string;
  onChange: (html: string) => void;
  /** The editor is ready; the handle inserts at its cursor. */
  onReady: (handle: BodyEditorHandle) => void;
  onFocus: () => void;
  label: string;
}

const OwnTextBodyEditor: React.FC<OwnTextBodyEditorProps> = ({
  initialValue,
  onChange,
  onReady,
  onFocus,
  label,
}) => (
  <TinyMCEEditor
    tinymceScriptSrc="/tinymce/tinymce.min.js"
    initialValue={initialValue}
    onEditorChange={(html: string) => onChange(html)}
    onInit={(_evt: unknown, editor: TinyEditor) =>
      onReady({ insertContent: (text) => editor.insertContent(text) })
    }
    licenseKey="gpl"
    init={{
      height: 360,
      menubar: false,
      z_index: 1400, // above the MUI Dialog (1300) when the composer is full screen
      plugins: 'code table lists link',
      toolbar:
        'code | blocks | bold italic underline' +
        ' | alignleft aligncenter alignright' +
        ' | bullist numlist | outdent indent | hr | link | table',
      entity_encoding: 'raw',
      valid_elements: '*[*]',
      branding: false,
      iframe_aria_text: label,
      setup: (editor: TinyEditor) => {
        editor.on('focus', onFocus);
      },
    }}
  />
);

export default OwnTextBodyEditor;
