import { Component, OnInit, Input } from '@angular/core';
import { ITopic } from '../topic';
import { TopicService } from '../topic.service';
import { ISession } from '../session';
import { SessionService } from '../session.service';
import { IPicture } from '../picture';
import { PictureService } from '../picture.service';
import { Router, ActivatedRoute } from '@angular/router';

@Component({
  selector: 'app-archiv-detail',
  templateUrl: './archiv-detail.component.html',
  styleUrls: ['./archiv-detail.component.css']
})
export class ArchivDetailComponent implements OnInit {

  @Input()
  bat: number;

  errorMessage = '';
  sessions: ISession[];
  topic: ITopic;
  pictures: IPicture[];

  constructor(private route: ActivatedRoute,
    private router: Router,
    private sessionService: SessionService,
    private topicService: TopicService,
    private pictureService: PictureService) {
  }

  ngOnInit() {
    const param = this.route.snapshot.paramMap.get('bat');

    if (param) {
      this.bat = +param;
    }
    this.getSessionByBat(this.bat);
    this.topicService.getTopicByBat(this.bat).subscribe(
      topic => this.topic = topic,
      error => this.errorMessage = <any>error);;
    this.pictureService.getPicturesByBat(this.bat).subscribe(
      pictures => this.pictures = pictures,
      error => this.errorMessage = <any>error);;
  }

  getSessionByBat(id: number) {
    this.sessionService.getSessionsByBat(id).subscribe(
      sessions => this.sessions = sessions,
      error => this.errorMessage = <any>error);
  }

  getUrl(session: ISession) {
    let pdf: string;
    if(session?.pdf)
    {
      if (session.pdf.startsWith("http"))
      {
        pdf = session.pdf;
      }
      else
      {
        pdf = "http://www.berner-architekten-treffen.ch/archiv/"+session.bat+"/"+session.pdf;
      }
    }
    return pdf;
  }

}
