export const SCHEDULER_GRID_COLUMNS_PER_CHAIR = {
  5: 4,
  10: 3,
  15: 2,
  20: 3,
  30: 2
} as const;

export type SchedulerGridSlotMinutes = keyof typeof SCHEDULER_GRID_COLUMNS_PER_CHAIR;

export interface SchedulerGridSettings {
  readonly slotMinutes: unknown;
  readonly startTime: unknown;
  readonly endTime: unknown;
}

export interface SchedulerGridLayout {
  readonly slotMinutes: SchedulerGridSlotMinutes;
  readonly start: number;
  readonly end: number;
  readonly totalSlots: number;
  readonly slotsPerHour: number;
  readonly columnsPerChair: number;
  readonly rowsPerHour: number;
  readonly visualRows: number;
}

export interface SchedulerGridPosition {
  readonly row: number;
  readonly column: number;
  readonly subColumn: number;
  readonly hourGroup: number;
}

export interface SchedulerAppointmentSegment {
  readonly row: number;
  readonly rowEnd: number;
  readonly columnStart: number;
  readonly columnEnd: number;
  readonly slotCount: number;
}

export interface SchedulerAppointmentBridge {
  readonly row: number;
  readonly rowEnd: number;
  readonly columnStart: number;
  readonly columnEnd: number;
}

export type SchedulerAppointmentCornerClass =
  | 'has-convex-top-left'
  | 'has-convex-top-right'
  | 'has-convex-bottom-left'
  | 'has-convex-bottom-right'
  | 'has-concave-top-left'
  | 'has-concave-top-right'
  | 'has-concave-bottom-left'
  | 'has-concave-bottom-right';

export interface SchedulerGridInclusiveRange {
  readonly startSlot: number;
  readonly endSlotExclusive: number;
  readonly slotCount: number;
  readonly startMinutes: number;
  readonly endMinutesInclusive: number;
  readonly startLabel: string;
  readonly endLabel: string;
  readonly label: string;
}

/**
 * Reproduces the compact legacy scheduler matrix. One visual hour is folded into
 * a small rectangle whose dimensions depend on the configured clinical slot.
 */
export function schedulerGridLayout(settings: SchedulerGridSettings): SchedulerGridLayout {
  const slotMinutes = supportedSlotMinutes(settings.slotMinutes);
  const start = clockMinutes(settings.startTime, 8 * 60);
  const configuredEnd = clockMinutes(settings.endTime, 16 * 60);
  const end = configuredEnd > start ? configuredEnd : start + slotMinutes;
  const slotsPerHour = 60 / slotMinutes;
  const columnsPerChair = SCHEDULER_GRID_COLUMNS_PER_CHAIR[slotMinutes];
  const rowsPerHour = slotsPerHour / columnsPerChair;
  const totalSlots = Math.max(1, Math.floor((end - start) / slotMinutes));
  const hourGroups = Math.ceil(totalSlots / slotsPerHour);
  return {
    slotMinutes,
    start,
    end,
    totalSlots,
    slotsPerHour,
    columnsPerChair,
    rowsPerHour,
    visualRows: hourGroups * rowsPerHour
  };
}

/** CSS-grid coordinates are one-based; row 1 is reserved for chair headers. */
export function schedulerGridPosition(
  slotIndex: number,
  chair: number,
  layout: SchedulerGridLayout,
  firstChair = 1
): SchedulerGridPosition {
  requireIntegerAtLeast(slotIndex, 0, 'slotIndex');
  requireIntegerAtLeast(chair, 1, 'chair');
  requireIntegerAtLeast(firstChair, 1, 'firstChair');
  if (slotIndex >= layout.totalSlots) throw new RangeError('slotIndex is outside the scheduler grid.');
  if (chair < firstChair) throw new RangeError('chair is before the visible viewport.');
  const hourGroup = Math.floor(slotIndex / layout.slotsPerHour);
  const withinHour = slotIndex % layout.slotsPerHour;
  const subColumn = withinHour % layout.columnsPerChair;
  return {
    row: 2 + hourGroup * layout.rowsPerHour + Math.floor(withinHour / layout.columnsPerChair),
    column: 1 + (chair - firstChair) * layout.columnsPerChair + subColumn,
    subColumn,
    hourGroup
  };
}

/**
 * Groups occupied slots into the fewest rectangular fragments. Consecutive
 * equal-width rows merge vertically; irregular starts and ends remain separate
 * so the UI can render the original puzzle-piece silhouette.
 */
export function schedulerAppointmentSegments(
  startSlot: number,
  span: number,
  chair: number,
  layout: SchedulerGridLayout,
  firstChair = 1
): SchedulerAppointmentSegment[] {
  requireIntegerAtLeast(startSlot, 0, 'startSlot');
  requireIntegerAtLeast(span, 1, 'span');
  if (startSlot >= layout.totalSlots) return [];
  const horizontalSegments: MutableSchedulerAppointmentSegment[] = [];
  const lastSlot = Math.min(layout.totalSlots, startSlot + span);
  for (let slotIndex = startSlot; slotIndex < lastSlot; slotIndex += 1) {
    const position = schedulerGridPosition(slotIndex, chair, layout, firstChair);
    const current = horizontalSegments.at(-1);
    if (current && current.row === position.row && current.columnEnd === position.column) {
      current.columnEnd += 1;
      current.slotCount += 1;
    } else {
      horizontalSegments.push({
        row: position.row,
        rowEnd: position.row + 1,
        columnStart: position.column,
        columnEnd: position.column + 1,
        slotCount: 1
      });
    }
  }
  return horizontalSegments.reduce<MutableSchedulerAppointmentSegment[]>((merged, segment) => {
    const previous = merged.at(-1);
    if (
      previous
      && previous.rowEnd === segment.row
      && previous.columnStart === segment.columnStart
      && previous.columnEnd === segment.columnEnd
    ) {
      previous.rowEnd = segment.rowEnd;
      previous.slotCount += segment.slotCount;
    } else {
      merged.push({ ...segment });
    }
    return merged;
  }, []);
}

/** Fills the visual gap only when two consecutive fragments do not overlap. */
export function schedulerAppointmentBridge(
  previous: SchedulerAppointmentSegment | undefined,
  current: SchedulerAppointmentSegment | undefined
): SchedulerAppointmentBridge | null {
  if (!previous || !current || previous.rowEnd !== current.row) return null;
  const overlapStart = Math.max(previous.columnStart, current.columnStart);
  const overlapEnd = Math.min(previous.columnEnd, current.columnEnd);
  if (overlapStart < overlapEnd) return null;
  return {
    row: current.row,
    rowEnd: current.rowEnd,
    columnStart: Math.min(previous.columnStart, current.columnStart),
    columnEnd: Math.max(previous.columnEnd, current.columnEnd)
  };
}

/** Classifies every external and internal corner exactly as the legacy grid did. */
export function schedulerAppointmentCornerClasses(
  segments: readonly SchedulerAppointmentSegment[],
  index: number
): SchedulerAppointmentCornerClass[] {
  requireIntegerAtLeast(index, 0, 'index');
  const segment = segments[index];
  if (!segment) throw new RangeError('index is outside the appointment segments.');
  const previous = index > 0 && segments[index - 1]?.rowEnd === segment.row ? segments[index - 1] : undefined;
  const next = index < segments.length - 1 && segment.rowEnd === segments[index + 1]?.row ? segments[index + 1] : undefined;
  const classes: SchedulerAppointmentCornerClass[] = [];
  if (!previous) {
    classes.push('has-convex-top-left', 'has-convex-top-right');
  } else {
    if (segment.columnStart !== previous.columnStart) {
      classes.push(segment.columnStart < previous.columnStart ? 'has-convex-top-left' : 'has-concave-top-left');
    }
    if (segment.columnEnd !== previous.columnEnd) {
      classes.push(segment.columnEnd > previous.columnEnd ? 'has-convex-top-right' : 'has-concave-top-right');
    }
  }
  if (!next) {
    classes.push('has-convex-bottom-left', 'has-convex-bottom-right');
  } else {
    if (segment.columnStart !== next.columnStart) {
      classes.push(segment.columnStart < next.columnStart ? 'has-convex-bottom-left' : 'has-concave-bottom-left');
    }
    if (segment.columnEnd !== next.columnEnd) {
      classes.push(segment.columnEnd > next.columnEnd ? 'has-convex-bottom-right' : 'has-concave-bottom-right');
    }
  }
  return classes;
}

/** Returns the real occupied range, ending at the final minute rather than the next slot boundary. */
export function schedulerGridInclusiveRange(
  startSlot: number,
  span: number,
  layout: SchedulerGridLayout
): SchedulerGridInclusiveRange | null {
  requireIntegerAtLeast(startSlot, 0, 'startSlot');
  requireIntegerAtLeast(span, 1, 'span');
  if (startSlot >= layout.totalSlots) return null;
  const endSlotExclusive = Math.min(layout.totalSlots, startSlot + span);
  const slotCount = endSlotExclusive - startSlot;
  const startMinutes = layout.start + startSlot * layout.slotMinutes;
  const endMinutesInclusive = startMinutes + slotCount * layout.slotMinutes - 1;
  const startLabel = clockLabel(startMinutes);
  const endLabel = clockLabel(endMinutesInclusive);
  return {
    startSlot,
    endSlotExclusive,
    slotCount,
    startMinutes,
    endMinutesInclusive,
    startLabel,
    endLabel,
    label: `${startLabel} a ${endLabel}`
  };
}

interface MutableSchedulerAppointmentSegment {
  row: number;
  rowEnd: number;
  columnStart: number;
  columnEnd: number;
  slotCount: number;
}

function supportedSlotMinutes(value: unknown): SchedulerGridSlotMinutes {
  const interval = Number(value);
  return Object.hasOwn(SCHEDULER_GRID_COLUMNS_PER_CHAIR, interval)
    ? interval as SchedulerGridSlotMinutes
    : 10;
}

function clockMinutes(value: unknown, fallback: number): number {
  if (Number.isInteger(value) && Number(value) >= 0 && Number(value) <= 24 * 60) return Number(value);
  const match = /^(\d{1,2}):(\d{2})$/.exec(String(value ?? '').trim());
  if (!match) return fallback;
  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  return hours >= 0 && hours <= 23 && minutes >= 0 && minutes <= 59 ? hours * 60 + minutes : fallback;
}

function clockLabel(minutes: number): string {
  const normalized = ((minutes % (24 * 60)) + 24 * 60) % (24 * 60);
  return `${String(Math.floor(normalized / 60)).padStart(2, '0')}:${String(normalized % 60).padStart(2, '0')}`;
}

function requireIntegerAtLeast(value: number, minimum: number, name: string): void {
  if (!Number.isInteger(value) || value < minimum) throw new RangeError(`${name} must be an integer of at least ${minimum}.`);
}
