import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { PercentPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { InventoryApiService } from '../inventory-api.service';
import { InventoryRefreshService } from '../inventory-refresh.service';
import { AllocationDetail, ApiError, displayEnum } from '../models';

@Component({
  selector: 'app-allocation-detail',
  imports: [PercentPipe, RouterLink, NgIcon],
  templateUrl: './allocation-detail.component.html',
})
export class AllocationDetailComponent {
  private readonly api = inject(InventoryApiService);
  private readonly refresh = inject(InventoryRefreshService);
  private readonly route = inject(ActivatedRoute);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly displayEnum = displayEnum;

  allocation: AllocationDetail | null = null;
  loading = true;
  actionPending = false;
  returnPendingId: number | null = null;
  loadError = '';
  actionError = '';

  constructor() {
    this.load();
  }

  load(): void {
    const id = Number(this.route.snapshot.paramMap.get('id'));
    if (!Number.isInteger(id)) {
      this.loading = false;
      this.loadError = 'This allocation request could not be found.';
      return;
    }
    this.loading = true;
    this.loadError = '';
    this.api.getAllocation(id).subscribe({
      next: (allocation) => {
        this.allocation = allocation;
        this.loading = false;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.loadError = 'Could not load this allocation request.';
        this.loading = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  act(action: 'confirm' | 'cancel'): void {
    if (!this.allocation) return;
    this.actionPending = true;
    this.actionError = '';
    const request = action === 'confirm'
      ? this.api.confirmAllocation(this.allocation.id)
      : this.api.cancelAllocation(this.allocation.id);
    request.subscribe({
      next: (allocation) => {
        this.allocation = allocation;
        this.actionPending = false;
        this.refresh.notify();
        this.changeDetector.markForCheck();
      },
      error: (response: HttpErrorResponse) => {
        const error = response.error as ApiError | undefined;
        this.actionError = error?.message ?? `Could not ${action} allocation.`;
        this.actionPending = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  returnEquipment(equipmentId: number): void {
    if (!this.allocation) return;
    this.returnPendingId = equipmentId;
    this.actionError = '';
    this.api.returnEquipment(this.allocation.id, equipmentId).subscribe({
      next: (allocation) => {
        this.allocation = allocation;
        this.returnPendingId = null;
        this.refresh.notify();
        this.changeDetector.markForCheck();
      },
      error: (response: HttpErrorResponse) => {
        const error = response.error as ApiError | undefined;
        this.actionError = error?.message ?? 'Could not return this device.';
        this.returnPendingId = null;
        this.changeDetector.markForCheck();
      },
    });
  }
}
