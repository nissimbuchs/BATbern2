import { Component, OnInit } from '@angular/core';
import { ITopic } from '../topic';
import { TopicService } from '../topic.service';

@Component({
  selector: 'app-aktuell',
  templateUrl: './aktuell.component.html',
  styleUrls: ['./aktuell.component.css']
})
export class AktuellComponent implements OnInit {

  errorMessage = '';
  currentTopic: ITopic;
  plannedTopics: ITopic[];

  constructor(private topicService: TopicService) {
  }

  ngOnInit(): void {
    this.topicService.getCurrentTopic().subscribe(
      product => {
        this.currentTopic = product;
      },
      error => this.errorMessage = <any>error
    );

    this.topicService.getPlannedTopics().subscribe(
      plannedTopics => {
        this.plannedTopics = plannedTopics;
      },
      error => this.errorMessage = <any>error
    );
  }

  onResize(event) {
    //console.log("resize "+event.target.innerHeight);
  }

  calculateHeight() {
    let style =+(window.innerHeight-60)+"px";
    //console.log("innerHeight: "+style);
    return style;
  }

}