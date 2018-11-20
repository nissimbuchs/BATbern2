import { Component } from '@angular/core';

@Component({
  selector: 'app-layout',
  template: `
  <clr-main-container >
    <app-header></app-header>
    <div class="content-container">
      <div class="content-area" data-spy="scroll" data-target="app-header">
        <ng-content></ng-content>
        <router-outlet></router-outlet>
        </div>
    </div>
  </clr-main-container>
  `,
  styles: [`
  clr-main-container .content-container .content-area {

    padding: 0pt;
  }`
]
})
export class LayoutComponent { }
