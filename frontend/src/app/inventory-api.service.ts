import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  AllocationDetail,
  AllocationSummary,
  CreateEquipment,
  Equipment,
  EquipmentState,
  EquipmentType,
  PolicySlot,
} from './models';

@Injectable({ providedIn: 'root' })
export class InventoryApiService {
  private readonly baseUrl = '/api';
  private readonly http = inject(HttpClient);

  getEquipment(state?: EquipmentState, type?: EquipmentType): Observable<Equipment[]> {
    let params = new HttpParams();
    if (state) params = params.set('state', state);
    if (type) params = params.set('type', type);
    return this.http.get<Equipment[]>(`${this.baseUrl}/equipments`, { params });
  }

  getBrands(): Observable<string[]> {
    return this.http.get<string[]>(`${this.baseUrl}/equipments/brands`);
  }

  createEquipment(request: CreateEquipment): Observable<Equipment> {
    return this.http.post<Equipment>(`${this.baseUrl}/equipments`, request);
  }

  getAllocations(): Observable<AllocationSummary[]> {
    return this.http.get<AllocationSummary[]>(`${this.baseUrl}/allocations`);
  }

  getAllocation(id: number): Observable<AllocationDetail> {
    return this.http.get<AllocationDetail>(`${this.baseUrl}/allocations/${id}`);
  }

  createAllocation(employeeId: string, policy: PolicySlot[]): Observable<AllocationDetail> {
    return this.http.post<AllocationDetail>(`${this.baseUrl}/allocations`, { employeeId, policy });
  }

  confirmAllocation(id: number): Observable<AllocationDetail> {
    return this.http.post<AllocationDetail>(`${this.baseUrl}/allocations/${id}/confirm`, {});
  }

  cancelAllocation(id: number): Observable<AllocationDetail> {
    return this.http.post<AllocationDetail>(`${this.baseUrl}/allocations/${id}/cancel`, {});
  }
}

