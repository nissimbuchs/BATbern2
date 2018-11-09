import { Routes } from '@angular/router'
import { VereinComponent } from './events/verein/verein.component';
import { AktuellComponent } from './events/aktuell/aktuell.component';
import { AnmeldungComponent } from './events/anmeldung/anmeldung.component';
import { ThemenComponent } from './events/themen/themen.component';
import { ArchivComponent } from './events/archiv/archiv.component';
import { KontaktComponent } from './events/kontakt/kontakt.component';

export const appRoutes:Routes = [
  { path: 'aktuell', component: AktuellComponent},
  { path: 'anmeldung', component: AnmeldungComponent},
  { path: 'themen', component: ThemenComponent},
  { path: 'archiv', component: ArchivComponent},
  { path: 'verein', component: VereinComponent},
  { path: 'kontakt', component: KontaktComponent},
  { path: '', redirectTo: '/aktuell', pathMatch: 'full'},
  { path: '**', redirectTo: '/aktuell'}
]