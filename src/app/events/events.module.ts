import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { VereinComponent } from './verein/verein.component';
import { AktuellComponent } from './aktuell/aktuell.component';
import { AnmeldungComponent } from './anmeldung/anmeldung.component';
import { ThemenComponent } from './themen/themen.component';
import { ArchivComponent } from './archiv/archiv.component';
import { KontaktComponent } from './kontakt/kontakt.component';
import { ClarityModule, ClrFormsNextModule } from '@clr/angular';

@NgModule({
  imports: [
    CommonModule,
    ClarityModule,
    ClrFormsNextModule
  ],
  declarations: [
    VereinComponent,
    AktuellComponent,
    AnmeldungComponent,
    ThemenComponent,
    ArchivComponent,
    KontaktComponent
  ],
  exports: [
    AktuellComponent
  ]
})
export class EventsModule { }
