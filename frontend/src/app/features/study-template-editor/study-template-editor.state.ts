import { StudyTemplateEditorSource } from './study-template-editor.models';

/** Tracks source identity and invalidates image requests without losing a loaded draft. */
export class StudyTemplateImageSession {
  private source: StudyTemplateEditorSource | null = null;
  private revision = 0;

  setSource(source: StudyTemplateEditorSource | null): boolean {
    const previous = this.source;
    const same = source === null ? previous === null : previous !== null
      && source.file === previous.file && source.url === previous.url
      && source.name === previous.name && source.title === previous.title;
    if (same) return false;
    this.source = source ? { ...source } : null;
    this.cancelLoad();
    return true;
  }

  beginLoad(): number { return ++this.revision; }
  isCurrent(revision: number): boolean { return revision === this.revision; }
  cancelLoad(): void { this.revision++; }
}
