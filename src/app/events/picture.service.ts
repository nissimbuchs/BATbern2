import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, tap, map } from 'rxjs/operators';

import { IPicture } from './picture';

@Injectable({
  providedIn: 'root'
})
export class PictureService {
  private pictureUrl = './api/pictures.json';

  constructor(private http: HttpClient) { }

  getPictures(): Observable<IPicture[]> {
    return this.http.get<IPicture[]>(this.pictureUrl).pipe();
  }

  getPicturesByBat(id: number): Observable<IPicture[] | undefined> {
    return this.getPictures().pipe(
      map((pictures: IPicture[]) => pictures.filter(p => p.bat === id))
    );
  }

  private handleError(err: HttpErrorResponse) {
    // in a real world app, we may send the server to some remote logging infrastructure
    // instead of just logging it to the console
    let errorMessage = '';
    if (err.error instanceof ErrorEvent) {
      // A client-side or network error occurred. Handle it accordingly.
      errorMessage = `An error occurred: ${err.error.message}`;
    } else {
      // The backend returned an unsuccessful response code.
      // The response body may contain clues as to what went wrong,
      errorMessage = `Server returned code: ${err.status}, error message is: ${err.message}`;
    }
    console.error(errorMessage);
    return throwError(errorMessage);
  }
}
