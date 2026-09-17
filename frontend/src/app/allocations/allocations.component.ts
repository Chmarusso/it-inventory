import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { PercentPipe } from '@angular/common';
import { InventoryApiService } from '../inventory-api.service';
import { InventoryRefreshService } from '../inventory-refresh.service';
import { BrandComboboxComponent } from '../shared/brand-combobox.component';
import { AllocationDetail, AllocationSummary, ApiError, PolicySlot, displayEnum, equipmentTypes } from '../models';

@Component({
  selector: 'app-allocations',
  imports: [FormsModule, PercentPipe, BrandComboboxComponent],
  templateUrl: './allocations.component.html',
})
export class AllocationsComponent {
  private readonly api = inject(InventoryApiService);
  private readonly refresh = inject(InventoryRefreshService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly types = equipmentTypes;
  readonly displayEnum = displayEnum;

  allocations: AllocationSummary[] = [];
  selected: AllocationDetail | null = null;
  employeeId = '';
  policy: PolicySlot[] = [this.emptySlot()];
  loading = false;
  detailLoading = false;
  saving = false;
  actionPending = false;
  listError = '';
  formError = '';
  actionError = '';
  brands: string[] = [];
  brandsLoading = false;

  constructor() {
    this.load();
    this.loadBrands();
  }

  /** Brands in stock feed every slot's picker; free text stays valid for brands not yet stocked. */
  private loadBrands(): void {
    this.brandsLoading = true;
    this.api.getBrands().subscribe({
      next: (brands) => { this.brands = brands; this.brandsLoading = false; this.changeDetector.markForCheck(); },
      // The picker degrades to a plain text field without the list, so this failure is silent.
      error: () => { this.brands = []; this.brandsLoading = false; this.changeDetector.markForCheck(); },
    });
  }

  load(): void {
    this.loading = true;
    this.listError = '';
    this.api.getAllocations().subscribe({
      next: (items) => { this.allocations = items; this.loading = false; this.changeDetector.markForCheck(); },
      error: () => { this.listError = 'Could not load allocation requests. Try again.'; this.loading = false; this.changeDetector.markForCheck(); },
    });
  }

  addSlot(): void { this.policy.push(this.emptySlot()); }
  removeSlot(index: number): void { if (this.policy.length > 1) this.policy.splice(index, 1); }

  create(): void {
    this.saving = true;
    this.formError = '';
    this.api.createAllocation(this.employeeId, this.policy).subscribe({
      next: (allocation) => {
        this.selected = allocation;
        this.employeeId = '';
        this.policy = [this.emptySlot()];
        this.saving = false;
        this.refresh.notify();
        this.load();
        this.changeDetector.markForCheck();
      },
      error: (response: HttpErrorResponse) => {
        const error = response.error as ApiError | undefined;
        this.formError = error?.message ?? 'Could not create allocation request.';
        this.saving = false;
        this.changeDetector.markForCheck();
      },
    });
  }

  view(id: number): void {
    this.detailLoading = true;
    this.actionError = '';
    this.api.getAllocation(id).subscribe({
      next: (allocation) => { this.selected = allocation; this.detailLoading = false; this.changeDetector.markForCheck(); },
      error: () => { this.actionError = 'Could not load request details.'; this.detailLoading = false; this.changeDetector.markForCheck(); },
    });
  }

  act(action: 'confirm' | 'cancel'): void {
    if (!this.selected) return;
    this.actionPending = true;
    this.actionError = '';
    const request = action === 'confirm'
      ? this.api.confirmAllocation(this.selected.id)
      : this.api.cancelAllocation(this.selected.id);
    request.subscribe({
      next: (allocation) => {
        this.selected = allocation;
        this.actionPending = false;
        this.refresh.notify();
        this.load();
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

  private emptySlot(): PolicySlot {
    return { type: 'MAIN_COMPUTER', minimumCondition: null, preferredBrand: '', preferRecent: true };
  }
}
