import { Component, OnInit } from '@angular/core';
import { ITopic } from '../topic';
import { TopicService } from '../topic.service';

@Component({
  selector: 'app-archiv',
  templateUrl: './archiv.component.html',
  styles: [`
    .leftCol {
      text-align: left;
    }
    @media (max-width: 630px) {
    .slideshareframe {
      display:none;
    }}`]
})
export class ArchivComponent implements OnInit {

  pageTitle = 'BAT Themen Liste';
  imageWidth = 50;
  imageMargin = 2;
  showImage = false;
  errorMessage = '';

  topics: ITopic[] = [];

  constructor(private topicService: TopicService) {
  }

  ngOnInit(): void {
    this.topicService.getTopics().subscribe(
      products => {
        this.topics = products;
        this.topics.sort((a,b) => b.bat - a.bat);
      },
      error => this.errorMessage = <any>error
    );
  }
}
