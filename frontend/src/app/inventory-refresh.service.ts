import { Injectable, signal } from '@angular/core';

@Injectable({ providedIn: 'root' })
export class InventoryRefreshService {
  readonly version = signal(0);
  notify(): void { this.version.update((value) => value + 1); }
}

