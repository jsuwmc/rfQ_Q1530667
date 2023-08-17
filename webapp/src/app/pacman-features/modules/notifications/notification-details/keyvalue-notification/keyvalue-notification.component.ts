import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { WorkflowService } from 'src/app/core/services/workflow.service';
import { LoggerService } from 'src/app/shared/services/logger.service';
import { UtilsService } from 'src/app/shared/services/utils.service';

@Component({
    selector: 'app-keyvalue-notification',
    templateUrl: './keyvalue-notification.component.html',
    styleUrls: ['./keyvalue-notification.component.css'],
})
export class KeyvalueNotificationComponent implements OnInit {
    @Input() details: { [key: string]: unknown };
    @Input() title = '';

    constructor(private router: Router,
        private logger: LoggerService,
        private workflowService: WorkflowService,
        private utils: UtilsService,
        private activatedRoute: ActivatedRoute) {}

    ngOnInit(): void {}

    navigateTo(link: string) {
        try{
            const windowOrigin = window.location.origin;
            if (!link || !link.includes(windowOrigin)) {
                return;
            }
            const urlObj = this.utils.getParamsFromUrlSnippet(link);

            this.workflowService.addRouterSnapshotToLevel(
                this.router.routerState.snapshot.root,
                0,
                "Notification Details",
            );
            const parts = urlObj.url.replace(windowOrigin, "").split('/');
            const urlToNavigate = parts.slice(0, 5).join('/') + '/' + encodeURIComponent(parts.slice(5).join('/'));
            
            this.router
                .navigate(["../../.." + urlToNavigate], {
                    relativeTo: this.activatedRoute,
                    queryParamsHandling: "merge"
                })
                .then((response) => {
                    this.logger.log('info', 'Successfully navigated to details page: ' + response);
                })
                .catch((error) => {
                    this.logger.log('error', 'Error in navigation - ' + error);
                });
        }catch(e){
            this.logger.log("jsError", e);
        }
    }

    isObject(item) {
        return typeof item.value === 'object' || Array.isArray(item.value);
    }
}
