export type EquipmentType = 'MAIN_COMPUTER' | 'MONITOR' | 'KEYBOARD' | 'MOUSE';
export type EquipmentState = 'AVAILABLE' | 'RESERVED' | 'ASSIGNED';
export type AllocationState = 'RESERVED' | 'CONFIRMED' | 'CANCELLED' | 'FAILED';

export interface Equipment {
  id: number;
  type: EquipmentType;
  brand: string;
  model: string;
  state: EquipmentState;
  conditionScore: number;
  purchaseDate: string;
}

export interface CreateEquipment {
  type: EquipmentType;
  brand: string;
  model: string;
  conditionScore: number | null;
  purchaseDate: string;
}

export interface PolicySlot {
  type: EquipmentType;
  minimumCondition: number | null;
  preferredBrand: string;
  preferRecent: boolean;
}

export interface AllocationSummary {
  id: number;
  employeeId: string;
  state: AllocationState;
  itemCount: number;
  failureReason: string | null;
  createdAt: string;
}

export interface AllocationDetail extends Omit<AllocationSummary, 'itemCount'> {
  policy: PolicySlot[];
  allocatedEquipments: Equipment[];
}

export interface ApiError {
  message: string;
  fieldErrors?: Record<string, string>;
}

export const equipmentTypes: EquipmentType[] = ['MAIN_COMPUTER', 'MONITOR', 'KEYBOARD', 'MOUSE'];
export const equipmentStates: EquipmentState[] = ['AVAILABLE', 'RESERVED', 'ASSIGNED'];

export function displayEnum(value: string): string {
  return value.toLowerCase().replaceAll('_', ' ').replace(/^./, (letter) => letter.toUpperCase());
}

