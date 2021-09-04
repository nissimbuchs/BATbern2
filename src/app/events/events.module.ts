import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { VereinComponent } from './verein/verein.component';
import { AktuellComponent } from './aktuell/aktuell.component';
import { AnmeldungComponent } from './anmeldung/anmeldung.component';
import { ThemenComponent } from './themen/themen.component';
import { ArchivComponent } from './archiv/archiv.component';
import { KontaktComponent } from './kontakt/kontakt.component';
import { ClarityModule } from '@clr/angular';
import { RouterModule } from '@angular/router';
import { appRoutes } from '../router';
import { ArchivDetailComponent } from './archiv-detail/archiv-detail.component';

@NgModule({
  imports: [
    CommonModule,
    RouterModule.forRoot(appRoutes, { relativeLinkResolution: 'legacy' }),
    ClarityModule
  ],
  declarations: [
    VereinComponent,
    AktuellComponent,
    AnmeldungComponent,
    ThemenComponent,
    ArchivComponent,
    KontaktComponent,
    ArchivDetailComponent
  ],
  exports: [
    AktuellComponent
  ]
})
export class EventsModule { }
