import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LayoutComponent } from './layout/layout.component';
import { HeaderComponent } from './layout/header/header.component';
import { ClarityModule } from '@clr/angular';
import { RouterModule } from '@angular/router';
import { appRoutes } from '../router';

@NgModule({
  imports: [
    CommonModule,
    RouterModule.forRoot(appRoutes, { relativeLinkResolution: 'legacy' }),
    ClarityModule
  ],
  declarations: [
    LayoutComponent,
    HeaderComponent
  ],
  exports: [
    LayoutComponent
  ]
})
export class UiModule { }
