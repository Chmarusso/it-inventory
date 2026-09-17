import { Routes } from '@angular/router';
import { InventoryComponent } from './inventory/inventory.component';
import { AllocationsComponent } from './allocations/allocations.component';

export const routes: Routes = [
  { path: 'inventory', component: InventoryComponent, title: 'Inventory | IT Operations' },
  { path: 'allocations', component: AllocationsComponent, title: 'Allocations | IT Operations' },
  { path: '', pathMatch: 'full', redirectTo: 'inventory' },
  { path: '**', redirectTo: 'inventory' },
];

