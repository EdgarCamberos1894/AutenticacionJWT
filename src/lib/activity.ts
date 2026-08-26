import type { ActivityEntry } from './types';

type Listener = () => void;

const MAX_ENTRIES = 80;
let entries: ActivityEntry[] = [];
const listeners = new Set<Listener>();

function emit() {
  listeners.forEach((listener) => listener());
}

export const activityStore = {
  getSnapshot: () => entries,
  subscribe(listener: Listener) {
    listeners.add(listener);
    return () => { listeners.delete(listener); };
  },
  add(entry: Omit<ActivityEntry, 'id' | 'at'>) {
    entries = [
      {
        ...entry,
        id: crypto.randomUUID(),
        at: new Date().toISOString(),
      },
      ...entries,
    ].slice(0, MAX_ENTRIES);
    emit();
  },
  clear() {
    entries = [];
    emit();
  },
};
