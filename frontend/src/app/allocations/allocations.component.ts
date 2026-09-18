import { ChangeDetectorRef, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgIcon } from '@ng-icons/core';
import { InventoryApiService } from '../inventory-api.service';
import { AllocationSummary, displayEnum } from '../models';

@Component({
  selector: 'app-allocations',
  imports: [RouterLink, NgIcon],
  templateUrl: './allocations.component.html',
})
export class AllocationsComponent {
  private readonly api = inject(InventoryApiService);
  private readonly changeDetector = inject(ChangeDetectorRef);
  readonly displayEnum = displayEnum;

  allocations: AllocationSummary[] = [];
  loading = false;
  listError = '';

  constructor() {
    this.load();
  }

  load(): void {
    this.loading = true;
    this.listError = '';
    this.api.getAllocations().subscribe({
      next: (items) => {
        this.allocations = items;
        this.loading = false;
        this.changeDetector.markForCheck();
      },
      error: () => {
        this.listError = 'Could not load allocation requests. Try again.';
        this.loading = false;
        this.changeDetector.markForCheck();
      },
    });
  }
}
