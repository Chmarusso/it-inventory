import { Component } from '@angular/core';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

@Component({
  selector: 'app-root',
  imports: [RouterLink, RouterLinkActive, RouterOutlet],
  template: `
    <header class="app-header">
      <div class="shell header-inner">
        <div>
          <p class="eyebrow">Operations console</p>
          <h1>IT Inventory</h1>
        </div>
        <nav aria-label="Primary navigation">
          <a routerLink="/inventory" routerLinkActive="active">Inventory</a>
          <a routerLink="/allocations" routerLinkActive="active">Allocations</a>
        </nav>
      </div>
    </header>
    <main class="shell"><router-outlet /></main>
  `,
})
export class AppComponent {}

