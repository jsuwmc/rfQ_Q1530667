/*
 *Copyright 2018 T Mobile, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); You may not use
 * this file except in compliance with the License. A copy of the License is located at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, express or
 * implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

import { CdkDragDrop, moveItemInArray } from '@angular/cdk/drag-drop';
import { DecimalPipe } from '@angular/common';
import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import find from 'lodash/find';
import map from 'lodash/map';
import { Subscription } from 'rxjs';
import { AssetGroupObservableService } from 'src/app/core/services/asset-group-observable.service';
import { AssetTypeMapService } from 'src/app/core/services/asset-type-map.service';
import { DomainTypeObservableService } from 'src/app/core/services/domain-type-observable.service';
import { TableStateService } from 'src/app/core/services/table-state.service';
import { WindowExpansionService } from 'src/app/core/services/window-expansion.service';
import { WorkflowService } from 'src/app/core/services/workflow.service';
import { IssueFilterService } from 'src/app/pacman-features/services/issue-filter.service';
import { MultilineChartService } from 'src/app/pacman-features/services/multilinechart.service';
import { OverallComplianceService } from 'src/app/pacman-features/services/overall-compliance.service';
import { PacmanIssuesService } from 'src/app/pacman-features/services/pacman-issues.service';
import { DATA_MAPPING } from 'src/app/shared/constants/data-mapping';
import { ComponentKeys } from 'src/app/shared/constants/component-keys';
import { CommonResponseService } from 'src/app/shared/services/common-response.service';
import { DownloadService } from 'src/app/shared/services/download.service';
import { ErrorHandlingService } from 'src/app/shared/services/error-handling.service';
import { LoggerService } from 'src/app/shared/services/logger.service';
import { RefactorFieldsService } from 'src/app/shared/services/refactor-fields.service';
import { RouterUtilityService } from 'src/app/shared/services/router-utility.service';
import { UtilsService } from 'src/app/shared/services/utils.service';
import { environment } from 'src/environments/environment';
import {
  DasbhoardCollapsedDict,
  DashboardArrangementItems,
  DashboardArrangementService,
  DashboardContainerIndex,
} from '../services/dashboard-arrangement.service';
import { AgDomainObservableService } from 'src/app/core/services/ag-domain-observable.service';

@Component({
  selector: 'app-compliance-dashboard',
  templateUrl: './compliance-dashboard.component.html',
  styleUrls: ['./compliance-dashboard.component.css'],
  animations: [],
  providers: [
    CommonResponseService,
    DashboardArrangementService,
    DecimalPipe,
    ErrorHandlingService,
    IssueFilterService,
    LoggerService,
    MultilineChartService,
    OverallComplianceService,
  ],
})
export class ComplianceDashboardComponent implements OnInit, OnDestroy {
  @ViewChild('widget') widgetContainer: ElementRef;

  pageTitle = 'Overview';
  saveStateKey: String = ComponentKeys.Dashboard;
  filterArr: any = [];
  filterText;
  queryParamsWithoutFilter;
  subscriptionToAssetGroup: Subscription;
  selectedAssetGroup: string;
  showingArr: any;
  ruleCatFilter;
  noMinHeight = false;
  paginatorSize = 20;
  totalRows = 0;
  bucketNumber = 0;
  searchTxt = '';
  complianceTableData: any = [];
  currentFilterType;
  filterTypeLabels = [];
  filterTagLabels = {};
  filterTypeOptions: any = [];
  filters: any = [];
  filterTagOptions: any = [];
  selectedDomain: any = '';
  searchPassed = '';
  tableDataLoaded = false;
  showSearchBar = false;
  showAddRemoveCol = false;
  private trendDataSubscription: Subscription;
  private assetGroupSubscription: Subscription;
  private onFilterChange: Subscription;
  private routeSubscription: Subscription;
  private complianceTableSubscription: Subscription;
  private issueFilterSubscription: Subscription;
  private activatedRouteSubscription: Subscription;
  private subscriptionDomain: Subscription;
  public pageLevel = 0;
  dataSubscriber: any;
  policyData: {
    color: string[];
    data: any[];
    legend: string[];
    legendTextcolor: string;
    totalCount: number;
    link: boolean;
    styling: { cursor: string };
  };
  policyDataError = '';
  pacmanIssues: any;
  pacmanCategories: any[];
  showdata: boolean;
  error: boolean;
  loaded: boolean;
  fetchedViolations = false;
  widgetWidth2: number;
  breakpoint1: number;
  breakpoint2: number;
  breakpoint3: number;
  breakpoint4: number;
  tableTitle = 'Policy Compliance Overview';
  tableErrorMessage = '';
  errorMessage = '';
  headerColName;
  direction;
  complianceData = [];
  complianceDataError = '';
  assetsCountData = [];
  assetsCountDataError = '';
  breadcrumbArray = [];
  breadcrumbLinks = [];
  breadcrumbPresent = 'Dashboard';
  columnNamesMap = {
    name: 'Policy',
    failed: 'Violations',
    provider: 'Source',
    severity: 'Severity',
    policyCategory: 'Category',
  };
  columnWidths = { Policy: 3, Violations: 1, Source: 1, "Asset Type": 1, Severity: 1, Category: 1, Compliance: 1 };
  selectedRowIndex: number;
  centeredColumns = {
    Policy: false,
    Violations: true,
    Source: true,
    Severity: true,
    Category: true,
    Compliance: true,
  };

  columnsSortFunctionMap = {
    Severity: (a, b, isAsc) => {
      const severeness = {
        low: 1,
        medium: 2,
        high: 3,
        critical: 4,
        default: 5 * (isAsc ? 1 : -1),
      };

      const ASeverity = a['Severity'].valueText ?? 'default';
      const BSeverity = b['Severity'].valueText ?? 'default';
      if (severeness[ASeverity] == severeness[BSeverity]) {
        return a['Violations'].valueText < b['Violations'].valueText ? 1 : -1;
      }
      return (severeness[ASeverity] < severeness[BSeverity] ? -1 : 1) * (isAsc ? 1 : -1);
    },
    Category: (a, b, isAsc) => {
      const priority = {
        security: 4,
        operations: 3,
        cost: 2,
        tagging: 1,
        default: 5 * (isAsc ? 1 : -1),
      };

      const ACategory = a['Category'].valueText ?? 'default';
      const BCategory = b['Category'].valueText ?? 'default';
      if (priority[ACategory] == priority[BCategory]) {
        return a['Violations'] < b['Violations'] ? -1 : 1;
      }
      return (priority[ACategory] < priority[BCategory] ? -1 : 1) * (isAsc ? 1 : -1);
    },
    Compliance: (a: string, b: string, isAsc) => {
      a = a['Compliance'].valueText;
      b = b['Compliance'].valueText;

      if (a == 'NR') isAsc ? (a = '101%') : (a = '-1%');
      if (b == 'NR') isAsc ? (b = '101%') : (b = '-1%');

      a = a.substring(0, a.length - 1);
      b = b.substring(0, b.length - 1);

      const aNum = parseFloat(a);
      const bNum = parseFloat(b);

      return (aNum < bNum ? -1 : 1) * (isAsc ? 1 : -1);
    },
    Violations: (a: string, b: string, isAsc) => {
      a = a['Violations'].valueText;
      b = b['Violations'].valueText;

      return (a < b ? -1 : 1) * (isAsc ? 1 : -1);
    },
  };
  tableImageDataMap = {
    security: {
      image: 'category-security',
      imageOnly: true,
    },
    operations: {
      image: 'category-operations',
      imageOnly: true,
    },
    cost: {
      image: 'category-cost',
      imageOnly: true,
    },
    tagging: {
      image: 'category-tagging',
      imageOnly: true,
    },
    low: {
      image: 'violations-low-icon',
      imageOnly: true,
    },
    medium: {
      image: 'violations-medium-icon',
      imageOnly: true,
    },
    high: {
      image: 'violations-high-icon',
      imageOnly: true,
    },
    critical: {
      image: 'violations-critical-icon',
      imageOnly: true,
    },
  };
  state: any = {};
  whiteListColumns;
  displayedColumns;

  totalAssetsCountData = [];
  totalAssetsCountDataError = '';
  isStatePreserved = false;
  showDownloadBtn = true;
  tableScrollTop = 0;
  graphFromDate: Date = new Date(2022, 0, 1);
  graphToDate: Date = new Date();
  minDate: Date;

  dashboardContainers: DashboardArrangementItems;
  dashcobardCollapsedContainers: DasbhoardCollapsedDict;

  readonly dashcobardCollapsedContainersTitles: { [key: number]: string } = {
    [DashboardContainerIndex.VIOLATION_SEVERITY]: 'Violations by Severity',
    [DashboardContainerIndex.CATEGORY_COMPLIANCE]:
      'Category Compliance & Violations by Severity',
    [DashboardContainerIndex.ASSET_GRAPH]: 'Asset Graph',
    [DashboardContainerIndex.POLICY_OVERVIEW]: 'Policy Compliance Overview',
  };
  agDomainSubscription: Subscription;

  constructor(
    private activatedRoute: ActivatedRoute,
    private commonResponseService: CommonResponseService,
    private dashboardArrangementService: DashboardArrangementService,
    private downloadService: DownloadService,
    private errorHandling: ErrorHandlingService,
    private issueFilterService: IssueFilterService,
    private logger: LoggerService,
    private multilineChartService: MultilineChartService,
    private numbersPipe: DecimalPipe,
    private overallComplianceService: OverallComplianceService,
    private pacmanIssuesService: PacmanIssuesService,
    private refactorFieldsService: RefactorFieldsService,
    private router: Router,
    private routerUtilityService: RouterUtilityService,
    private tableStateService: TableStateService,
    private utils: UtilsService,
    private windowExpansionService: WindowExpansionService,
    private workflowService: WorkflowService,
    private agDomainObservableService: AgDomainObservableService,
    private assetTypeMapService: AssetTypeMapService
  ) {
    this.agDomainSubscription = this.agDomainObservableService.getAgDomain().subscribe(async ([assetGroupName, domain]) => {
      await this.getPreservedState();
      this.selectedAssetGroup = assetGroupName;
      this.selectedDomain = domain;
      this.updateComponent();
    })
  }

  async getPreservedState() {
    const state = this.tableStateService.getState(this.saveStateKey) ?? {};

    this.headerColName = state.headerColName ?? 'Severity';
    this.direction = state.direction ?? 'desc';
    this.displayedColumns = ["Policy", "Violations", "Source", "Severity", "Category", "Compliance"];
    // this.bucketNumber = state.bucketNumber ?? 0;
    this.whiteListColumns = state?.whiteListColumns ?? this.displayedColumns;
    this.complianceTableData = state?.data ?? [];
    this.tableDataLoaded = true;
    this.searchTxt = state?.searchTxt ?? '';
    this.tableScrollTop = state?.tableScrollTop;
    this.totalRows = state.totalRows ?? 0;
    this.filters = state?.filters ?? [];
    this.selectedRowIndex = state?.selectedRowIndex;

    if (this.complianceTableData && this.complianceTableData.length > 0) {
      this.isStatePreserved = true;
    } else {
      this.isStatePreserved = false;
    }
    if (state.filters) {
      this.filters = state.filters;
      await Promise.resolve().then(() => this.getUpdatedUrl());
    }
  }

  ngOnInit() {


    const breadcrumbInfo = this.workflowService.getDetailsFromStorage()["level0"];

    if (breadcrumbInfo) {
      this.breadcrumbArray = breadcrumbInfo.map(item => item.title);
      this.breadcrumbLinks = breadcrumbInfo.map(item => item.url);
    }
    this.breakpoint1 = window.innerWidth <= 800 ? 2 : 4;
    this.breakpoint2 = window.innerWidth <= 800 ? 1 : 2;
    this.breakpoint3 = window.innerWidth <= 400 ? 1 : 1;
    this.breakpoint4 = window.innerWidth <= 400 ? 1 : 1;
    this.dashboardContainers = this.dashboardArrangementService.getArrangement();
    this.dashcobardCollapsedContainers = this.dashboardArrangementService.getCollapsed();
  }

  massageAssetTrendGraphData(graphData) {
    const data = [];
    data.push({ key: 'Total Assets', values: [], info: {} });

    for (let i = 0; i < data.length; i++) {
      graphData.trend.forEach((e) => {
        data[i].values.push({
          date: new Date(e.date),
          value: e.totalassets,
          'zero-value': e.totalassets == 0,
        });
      });
    }
    data[0].values.sort(function (a, b) {
      return new Date(a.date) > new Date(b.date) ? 1 : -1;
    });

    if (!this.minDate) {
      this.minDate = data[0].values[0].date;
    }

    data[0].info = {
      id: 'TotalAssetsCountTrend',
      showLegend: true,
      yAxisLabel: 'Count',
      height: 320,
    };

    return data;
  }

  openOverAllComplianceTrendModal = () => {
    this.router.navigate(['/pl', { outlets: { modal: ['overall-compliance-trend'] } }], {
      queryParamsHandling: 'merge',
    });
  };

  openOverAllPolicyViolationsTrendModal = () => {
    this.router.navigate(['/pl', { outlets: { modal: ['policy-violations-trend'] } }], {
      queryParamsHandling: 'merge',
    });
  };

  navigateToAssetDistribution = () => {
    this.workflowService.addRouterSnapshotToLevel(
      this.router.routerState.snapshot.root,
      0,
      this.breadcrumbPresent,
    );
    this.router.navigate(['/pl/assets/asset-distribution/'], {
      queryParamsHandling: 'merge',
    });
  };

  violationCards = [
    {
      id: 1,
      name: 'critical',
      totalViolations: 0,
      subInfo: { Policies: { value: 0 }, Assets: { value: 0 } },
    },
    {
      id: 2,
      name: 'high',
      totalViolations: 0,
      subInfo: { Policies: { value: 0 }, Assets: { value: 0 } },
    },
    {
      id: 3,
      name: 'medium',
      totalViolations: 0,
      subInfo: { Policies: { value: 0 }, Assets: { value: 0 } },
    },
    {
      id: 4,
      name: 'low',
      totalViolations: 0,
      subInfo: { Policies: { value: 0 }, Assets: { value: 0 } },
    },
  ];

  cards = [
    {
      id: 1,
      header: 'Category Compliance',
      footer: 'View Trends',
      cardButtonAction: this.openOverAllComplianceTrendModal,
    },
    {
      id: 2,
      header: 'Violations by Severity',
      footer: 'View Trends',
      cardButtonAction: this.openOverAllPolicyViolationsTrendModal,
    },
    {
      id: 3,
      header: 'Asset Graph',
      footer: 'View Asset Distribution',
      cardButtonAction: this.navigateToAssetDistribution,
    },
  ];

  handleHeaderColNameSelection(event) {
    this.headerColName = event.headerColName;
    this.direction = event.direction;
    this.storeState();
  }

  handleWhitelistColumnsChange(event) {
    this.whiteListColumns = event;
    this.storeState();
  }

  handleFilterTypeSelection() {
    this.storeState();
  }

  handleFilterSelection() {
    this.storeState();
  }

  clearState() {
    // this.tableStateService.clearState(this.saveStateKey);
    this.isStatePreserved = false;
  }

  storeState(data?) {
    const state = {
      totalRows: this.totalRows,
      data: data,
      headerColName: this.headerColName,
      direction: this.direction,
      whiteListColumns: this.whiteListColumns,
      bucketNumber: this.bucketNumber,
      searchTxt: this.searchTxt,
      tableScrollTop: this.tableScrollTop,
      filters: this.filters,
      selectedRowIndex: this.selectedRowIndex
    }
    this.tableStateService.setState(this.saveStateKey, state);
  }

  getDistributionBySeverity() {
    const distributionBySeverityUrl = environment.distributionBySeverity.url;
    const distributionBySeverityMethod = environment.distributionBySeverity.method;
    const queryParams = {
      ag: this.selectedAssetGroup,
      domain: this.selectedDomain,
    };

    try {
      this.commonResponseService
        .getData(distributionBySeverityUrl, distributionBySeverityMethod, {}, queryParams)
        .subscribe((response) => {
          const data = response.distribution.distributionBySeverity;
          for (let i = 0; i < this.violationCards.length; i++) {
            const violationName = this.violationCards[i].name;
            if (data[violationName]) {
              this.violationCards[i].totalViolations =
                data[violationName].totalViolations;
              this.violationCards[i].subInfo = {
                Policies: { value: data[violationName].policyCount },
                Assets: { value: data[violationName].assetCount },
              };
            } else {
              this.violationCards[i].totalViolations = 0;
              this.violationCards[i].subInfo = {
                Policies: { value: 0 }, Assets: { value: 0 }
              };
            }
          }
        });
    } catch (error) { }
  }

  getPacmanIssues() {
    if (this.dataSubscriber) {
      this.dataSubscriber.unsubscribe();
    }
    const queryParams = {
      ag: this.selectedAssetGroup,
      domain: this.selectedDomain,
    };
    const pacmanIssuesUrl = environment.pacmanIssues.url;
    const pacmanIssuesMethod = environment.pacmanIssues.method;
    try {
      this.dataSubscriber = this.pacmanIssuesService
        .getData(queryParams, pacmanIssuesUrl, pacmanIssuesMethod)
        .subscribe(
          (response) => {
            try {
              if (response.err) {
                throw response;
              }
              this.pacmanIssues = response;
              this.pacmanCategories = [];
              for (let i = 0; i < this.pacmanIssues.category.length; i++) {
                const obj = {
                  displayName:
                    this.refactorFieldsService.getDisplayNameForAKey(
                      Object.keys(
                        this.pacmanIssues.category[i],
                      )[0].toLowerCase(),
                    ) || Object.keys(this.pacmanIssues.category[i])[0],
                  key: Object.keys(this.pacmanIssues.category[i])[0],
                  value: this.pacmanIssues.category[i][
                    Object.keys(this.pacmanIssues.category[i])[0]
                  ],
                };
                this.pacmanCategories.push(obj);
              }
              const dataValue = [];
              let totalCount = 0;
              for (let i = 0; i < this.pacmanIssues.severity.length; i++) {
                const count =
                  this.pacmanIssues.severity[i][
                  Object.keys(this.pacmanIssues.severity[i])[0]
                  ];
                totalCount += count;
                dataValue.push(count);
              }
              this.fetchedViolations = true;
              this.policyDataError = '';
              if (dataValue.length > 0) {
                this.policyData = {
                  color: ['#D14938', '#F58F6F', '#F5B66F', '#506EA7'],
                  data: dataValue,
                  legend: ['Critical', 'High', 'Medium', 'Low'],
                  legendTextcolor: '#000',
                  totalCount: totalCount,
                  link: true,
                  styling: {
                    cursor: 'pointer',
                  },
                };
              } else {
                this.policyDataError = 'noDataAvailable';
              }
              this.loaded = true;
              this.showdata = true;
              this.error = false;
            } catch (e) {
              this.policyDataError = 'apiResponseError';
              this.tableErrorMessage = this.errorHandling.handleJavascriptError(e);
              this.getErrorValues();
            }
          },
          (error) => {
            this.tableErrorMessage = error;
            this.getErrorValues();
          },
        );
    } catch (error) {
      this.tableErrorMessage = this.errorHandling.handleJavascriptError(error);
      this.getErrorValues();
    }
  }

  getErrorValues(): void {
    this.loaded = true;
    this.error = true;
  }

  routerParam() {
    try {
      // this.filterText saves the queryparam
      const currentQueryParams =
        this.routerUtilityService.getQueryParametersFromSnapshot(
          this.router.routerState.snapshot.root
        );
      if (currentQueryParams) {
        this.queryParamsWithoutFilter = JSON.parse(
          JSON.stringify(currentQueryParams)
        );
        delete this.queryParamsWithoutFilter["filter"];
        /**
         * The below code is added to get URLparameter and queryparameter
         * when the page loads ,only then this function runs and hits the api with the
         * filterText obj processed through processFilterObj function
         */
        this.filterText = this.utils.processFilterObj(currentQueryParams);
      }
    } catch (error) {
      this.errorMessage = this.errorHandling.handleJavascriptError(error);
      this.logger.log("error", error);
    }
  }
  getUpdatedUrl() {
    let updatedQueryParams = {};
    this.filterText = this.utils.arrayToObject(
      this.filters,
      "filterkey",
      "value"
    ); // <-- TO update the queryparam which is passed in the filter of the api
    this.filterText = this.utils.makeFilterObj(this.filterText);

    /**
     * To change the url
     * with the deleted filter value along with the other existing paramter(ex-->tv:true)
     */

    updatedQueryParams = {
      filter: this.filterText.filter,
    }


    /**
     * Finally after changing URL Link
     * api is again called with the updated filter
     */
    this.filterText = this.utils.processFilterObj(this.filterText);

    this.router.navigate([], {
      relativeTo: this.activatedRoute,
      queryParams: updatedQueryParams,
      queryParamsHandling: 'merge',
    });
  }
  deleteFilters(event?) {
    try {
      if (!event) {
        this.filters = [];
        this.storeState();
      } else if (event.removeOnlyFilterValue) {
        this.getUpdatedUrl();
        this.getData();
        this.storeState();
      } else if (event.index && !this.filters[event.index].filterValue) {
        this.filters.splice(event.index, 1);
        this.storeState();
      }
      else {
        if (event.clearAll) {
          this.filters = [];
        } else {
          this.filters.splice(event.index, 1);
        }
        this.storeState();
        this.getUpdatedUrl();
        this.getData();
      }
    } catch (error) { }
    /* TODO: Aditya: Why are we not calling any updateCompliance function in observable to update the filters */
  }
  /*
   * this functin passes query params to filter component to show filter
   */
  getFilterArray() {
    try {
      const filterObjKeys = Object.keys(this.filterText);
      const dataArray = [];
      for (let i = 0; i < filterObjKeys.length; i++) {
        let obj = {};
        const keyDisplayValue = find(this.filterTypeOptions, {
          optionValue: filterObjKeys[i],
        })["optionName"];
        obj = {
          keyDisplayValue,
          filterkey: filterObjKeys[i],
        };
        dataArray.push(obj);
      }

      const state = this.tableStateService.getState(this.pageTitle) ?? {};
      const filters = state?.filters;

      if (filters) {
        const dataArrayFilterKeys = dataArray.map(obj => obj.keyDisplayValue);
        filters.forEach(filter => {
          if (!dataArrayFilterKeys.includes(filter.keyDisplayValue)) {
            dataArray.push({
              filterkey: filter.filterkey,
              keyDisplayValue: filter.key
            });
          }
        });
      }

      const formattedFilters = dataArray;
      for (let i = 0; i < formattedFilters.length; i++) {

        let keyDisplayValue = formattedFilters[i].keyDisplayValue;
        if (!keyDisplayValue) {
          keyDisplayValue = find(this.filterTypeOptions, {
            optionValue: formattedFilters[i].filterKey,
          })["optionName"];
        }

        this.changeFilterType(keyDisplayValue).then(() => {
          let filterValueObj = find(this.filterTagOptions[keyDisplayValue], {
            id: this.filterText[formattedFilters[i].filterkey],
          });

          let filterKey = dataArray[i].filterkey;

          if (!this.filters.find(filter => filter.keyDisplayValue == keyDisplayValue)) {
            const eachObj = {
              keyDisplayValue: keyDisplayValue,
              filterValue: filterValueObj ? filterValueObj["name"] : undefined,
              key: keyDisplayValue, // <-- displayKey-- Resource Type
              value: this.filterText[filterKey], // <<-- value to be shown in the filter UI-- S2
              filterkey: filterKey?.trim(), // <<-- filter key that to be passed -- "resourceType "
              compareKey: filterKey?.toLowerCase().trim(), // <<-- key to compare whether a key is already present -- "resourcetype"
            };
            this.filters.push(eachObj);
            this.filters = [...this.filters];
            this.storeState();
          }
        })
      }
    } catch (error) {
      this.errorMessage = this.errorHandling.handleJavascriptError(error);
      this.logger.log("error", error);
    }
  }

  /**
   * This function get calls the keyword service before initializing
   * the filter array ,so that filter keynames are changed
   */

  getFilters() {
    return new Promise((resolve) => {
      try {
        this.issueFilterSubscription = this.issueFilterService
          .getFilters(
            { filterId: 13, domain: this.selectedDomain },
            environment.issueFilter.url,
            environment.issueFilter.method
          )
          .subscribe((response) => {
            this.filterTypeLabels = map(response[0].response, "optionName");
            resolve(true);
            this.filterTypeOptions = response[0].response;

            this.filterTypeLabels.sort();
            this.routerParam();
            // this.deleteFilters();
            this.getFilterArray();
          });
      } catch (error) {
        this.errorMessage = this.errorHandling.handleJavascriptError(error);
        this.logger.log("error", error);
        resolve(false);
      }
    });
  }

  changeFilterType(value) {
    return new Promise((resolve) => {
      try {
        this.currentFilterType = find(this.filterTypeOptions, {
          optionName: value,
        });

        const excludedKeys = [
          "domain",
          this.currentFilterType.optionValue
        ]
        let filtersToBePassed = this.getFilterPayloadForDataAPI();
        filtersToBePassed = Object.keys(filtersToBePassed).reduce((result, key) => {
          const normalizedKey = key.replace(".keyword", "");
          if ((!excludedKeys.includes(normalizedKey))) {
            result[normalizedKey] = filtersToBePassed[key];
          }
          return result;
        }, {});
        const payload = {
          attributeName: this.currentFilterType["optionValue"]?.replace(".keyword", ""),
          ag: this.selectedAssetGroup,
          domain: this.selectedDomain,
          filter: filtersToBePassed
        }
        this.issueFilterSubscription = this.issueFilterService
          .getFilters(
            {},
            environment.base +
            this.utils.getParamsFromUrlSnippet(this.currentFilterType.optionURL)
              .url,
            "POST",
            payload
          )
          .subscribe((response) => {
            let filterTagsData: { [key: string]: any }[] = [];
            if (!response[0].data.optionRange) {
              filterTagsData = (response[0].data.optionList || []).map(filterTag => {
                return { id: filterTag, name: filterTag };
              });
            }
            if (value.toLowerCase() == "asset type") {
              this.assetTypeMapService.getAssetMap().subscribe(assetTypeMap => {
                filterTagsData.map(filterOption => {
                  filterOption["name"] = assetTypeMap.get(filterOption["name"]?.toLowerCase()) || filterOption["name"]
                });
              });
            }
            else if (value.toLowerCase() === "violations" || value.toLowerCase() === "compliance") {
              const numOfIntervals = 5;
              const { min, max } = response[0].data.optionRange;
              const intervals = this.utils.generateIntervals(min, max, numOfIntervals);

              intervals.forEach(interval => {
                const lb = Math.round(interval.lowerBound);
                let up = Math.round(interval.upperBound);
                if (value.toLowerCase() === "compliance" && up === 100) {
                  up--;
                }
                filterTagsData.push({ id: `${lb}-${up}`, name: `${lb}-${up}` });
              });
              if (value.toLowerCase() === "compliance") {
                filterTagsData.push({ id: "100-100", name: "100-100" });
              }
            }
            this.filterTagOptions[value] = filterTagsData;
            if (value == "Compliance" || value == "Violations") {
              this.filterTagLabels[value] = filterTagsData.map(label => label.name);
            } else {
              this.filterTagLabels = {
                ...this.filterTagLabels,
                ...{
                  [value]: map(filterTagsData, 'name').sort((a, b) =>
                    a.localeCompare(b),
                  ),
                },
              };
            }
            resolve(this.filterTagOptions[value]);
            this.storeState();
          });
      } catch (error) {
        this.errorMessage = this.errorHandling.handleJavascriptError(error);
        this.logger.log("error", error);
      }
    });
  }

  changeFilterTags(event) {
    const filterValues = event.filterValue;

    this.currentFilterType = find(this.filterTypeOptions, {
      optionName: event.filterKeyDisplayValue,
    });
    try {
      if (this.currentFilterType) {
        const filterTags = filterValues.map(value => {
          const v = find(this.filterTagOptions[event.filterKeyDisplayValue], { name: value })["id"];
          return v;
        });

        this.utils.addOrReplaceElement(
          this.filters,
          {
            keyDisplayValue: event.filterKeyDisplayValue,
            filterValue: filterValues,
            key: this.currentFilterType.optionName,
            value: filterTags,
            filterkey: this.currentFilterType.optionValue.trim(),
            compareKey: this.currentFilterType.optionValue.toLowerCase().trim(),
          },
          (el) => {
            return (
              el.compareKey ===
              this.currentFilterType.optionValue.toLowerCase().trim()
            );
          }
        );
      }
      this.storeState();
      this.getUpdatedUrl();
      this.utils.clickClearDropdown();
      this.getData();
    } catch (error) {
      this.errorMessage = this.errorHandling.handleJavascriptError(error);
      this.logger.log("error", error);
    }
  }

  updateComponent() {
    if (this.complianceTableSubscription) {
      this.complianceTableSubscription.unsubscribe();
    }
    // below condition ensures that on initial landing, updatecomponent executes only once
    if (!this.selectedAssetGroup || !this.selectedDomain) {
      return;
    }
    this.ruleCatFilter = undefined;
    this.noMinHeight = false;
    // this.bucketNumber = 0;
    // this.currentPointer = 0;

    this.assetsCountData = [];
    this.assetsCountDataError = '';
    this.complianceData = [];
    this.complianceDataError = '';
    this.policyDataError = '';
    if (this.isStatePreserved) {
      this.tableDataLoaded = true;
      this.getFilters();
      // this.clearState();
    } else {
      this.tableScrollTop = 0;
      this.searchTxt = "";
      this.tableErrorMessage = '';
      this.errorMessage = '';
      this.tableDataLoaded = false;
      this.bucketNumber = 0;
      this.complianceTableData = [];
      this.getFilters().then(() => {
        this.getData();
      });
    }
    this.getDistributionBySeverity();
    this.getPacmanIssues();
    this.getAssetsCountData({});
    this.getComplianceData();
  }

  // changeFilterTags(value) {
  //   try {
  //     if (this.currentFilterType) {
  //       const filterTag = _.find(this.filterTagOptions, { name: value.value });
  //       this.utils.addOrReplaceElement(
  //         this.filters,
  //         {
  //           typeName: this.currentFilterType.optionName,
  //           typeValue: this.currentFilterType.optionValue,
  //           tagName: filterTag.name,
  //           tagValue: filterTag["id"],
  //           key: this.currentFilterType.optionName,
  //           value: filterTag.name,
  //         },
  //         (el) => {
  //           return el.key === this.currentFilterType.optionName;
  //         }
  //       );
  //       this.updateComponent();
  //     }
  //     this.utils.clickClearDropdown();
  //   } catch (error) {
  //     this.errorMessage = this.errorHandling.handleJavascriptError(error);
  //     this.logger.log("error", error);
  //   }
  // }

  getFormattedDate(date: Date) {
    const offset = date.getTimezoneOffset();
    const formattedDate = new Date(date.getTime() - offset * 60 * 1000)
      .toISOString()
      .split('T')[0];
    return formattedDate;
  }

  getAssetsCountData(queryObj) {
    if (!this.selectedAssetGroup) {
      return;
    }
    if (this.trendDataSubscription) {
      this.trendDataSubscription.unsubscribe();
    }
    if (queryObj.from) {
      this.graphFromDate = queryObj.from;
    }

    if (queryObj.to) {
      this.graphToDate = queryObj.to;
    }
    const queryParams = {
      ag: this.selectedAssetGroup,
      domain: this.selectedDomain,
      from: this.getFormattedDate(this.graphFromDate),
      to: this.getFormattedDate(this.graphToDate),
    };

    this.totalAssetsCountDataError = '';
    this.totalAssetsCountData = [];

    try {
      this.trendDataSubscription = this.multilineChartService
        .getAssetTrendData(queryParams)
        .subscribe(
          (response) => {
            this.totalAssetsCountData = this.massageAssetTrendGraphData(response[0]);
            this.getMinDateForDateSelector(this.totalAssetsCountData[0].values);
            if (this.utils.getDifferenceBetweenDateByDays(this.minDate, new Date()) < 2 && this.totalAssetsCountData[0].values.length < 2) {
              this.totalAssetsCountDataError = 'waitForData';
            } else if (this.totalAssetsCountData[0].values.length == 0) {
              this.totalAssetsCountDataError = 'noDataAvailable';
            }
          },
          (error) => {
            this.logger.log('error', error);
            this.totalAssetsCountDataError = 'apiResponseError';
          },
        );
    } catch (error) {
      this.totalAssetsCountDataError = 'apiResponseError';
      this.logger.log('error', error);
    }
  }

  processData(data) {
    try {
      let innerArr = {};
      const totalVariablesObj = {};
      let cellObj = {};
      let processedData = [];
      const getData = data;

      let cellData;
      for (let row = 0; row < getData.length; row++) {
        const keynames = Object.keys(getData[row]);
        innerArr = {};
        keynames.forEach((col) => {
          const isPolicyCol = col.toLowerCase() === 'policy';
          cellData = getData[row][col];
          cellObj = {
            text: this.tableImageDataMap[
              typeof cellData == 'string' ? cellData.toLowerCase() : cellData
            ]?.imageOnly
              ? ''
              : cellData, // text to be shown in table cell
            titleText: cellData == 'NR' ? 'No Resources' : cellData, // text to show on hover
            valueText: cellData,
            hasPostImage: false,
            imgSrc: this.tableImageDataMap[
              typeof cellData == 'string' ? cellData.toLowerCase() : cellData
            ]?.image, // if imageSrc is not empty and text is also not empty then this image comes before text otherwise if imageSrc is not empty and text is empty then only this image is rendered,
            postImgSrc: '',
            isChip: '',
            isMenuBtn: false,
            properties: '',
            isLink: isPolicyCol,
          };
          if (col.toLowerCase() === 'violations') {
            cellObj = {
              ...cellObj,
              text: this.numbersPipe.transform(cellData),
            };
          }
          innerArr[col] = cellObj;
          totalVariablesObj[col] = '';
        });
        processedData.push(innerArr);
      }
      if (processedData.length > getData.length) {
        const halfLength = processedData.length / 2;
        processedData = processedData.splice(halfLength);
      }
      return processedData;
    } catch (error) {
      this.errorMessage = this.errorHandling.handleJavascriptError(error);
      this.logger.log('error', error);
    }
  }

  getMinDateForDateSelector(dataList) {
    if (!this.minDate) {
      if (dataList.length > 0) {
        this.minDate = new Date(dataList[0].date);
      } else {
        this.minDate = new Date();
      }
    }
  }

  private getComplianceData() {
    if (!this.selectedAssetGroup || !this.selectedDomain) {
      return;
    }
    const queryParams = {
      ag: this.selectedAssetGroup,
      domain: this.selectedDomain,
    };

    const overallComplianceUrl = environment.overallCompliance.url;
    const overallComplianceMethod = environment.overallCompliance.method;
    this.overallComplianceService
      .getOverallCompliance(queryParams, overallComplianceUrl, overallComplianceMethod)
      .subscribe((response) => {
        try {
          if (response[0].error) {
            throw response[0];
          }
          this.complianceDataError = '';
          this.complianceData = [
            { class: '', title: 'Security', val: 'NR' },
            { class: '', title: 'Cost', val: 'NR' },
            { class: '', title: 'Operations', val: 'NR' },
            { class: '', title: 'Tagging', val: 'NR' },
          ];
          response[0].data.forEach((element) => {
            const category = element[1]['title'].toLowerCase();
            let index;
            switch (category) {
              case 'security':
                index = 0;
                break;
              case 'cost':
                index = 1;
                break;
              case 'operations':
                index = 2;
                break;
              case 'tagging':
                index = 3;
                break;
            }
            this.complianceData[index].val = element[1]['val'];

            if (element[1]['val'] <= 40) {
              this.complianceData[index].class = 'red';
            } else if (element[1]['val'] <= 75) {
              this.complianceData[index].class = 'or';
            } else {
              this.complianceData[index].class = 'gr';
            }
          });
          if (this.complianceData.length == 0) {
            this.complianceDataError = 'noDataAvailable';
          }
        } catch (error) {
          this.complianceDataError = 'apiResponseError';
          this.logger.log('error', error);
        }
      });
  }

  getFilterPayloadForDataAPI() {
    const filterToBePassed = { ...this.filterText };
    Object.keys(filterToBePassed).forEach(filterKey => {
      if (filterKey == "domain") return;
      filterToBePassed[filterKey] = filterToBePassed[filterKey].split(",");
      if (filterKey == "failed" || filterKey == "compliance_percent") {
        filterToBePassed[filterKey] = filterToBePassed[filterKey].map(filterVal => {
          const [min, max] = filterVal.split("-");
          return { min, max }
        })
      }
    })

    return filterToBePassed;
  }

  getData() {
    if (!this.selectedAssetGroup || !this.selectedDomain) {
      return;
    }
    const filterToBePassed = this.getFilterPayloadForDataAPI();

    const filters = { domain: this.selectedDomain };

    const payload = {
      ag: this.selectedAssetGroup,
      filter: filters,
      reqFilter: filterToBePassed,
      from: this.bucketNumber * this.paginatorSize,
      // searchtext: this.searchTxt,
    };

    this.tableErrorMessage = '';
    const complianceTableUrl = environment.complianceTable.url;
    const complianceTableMethod = environment.complianceTable.method;
    this.complianceTableSubscription = this.commonResponseService
      .getData(complianceTableUrl, complianceTableMethod, payload, {})
      .subscribe(
        (response) => {
          this.totalRows = response.total;
          try {
            const updatedResponse = this.massageData(response.data.response);
            const processedData = this.processData(updatedResponse);
            this.complianceTableData = processedData;
            this.tableDataLoaded = true;
            if (this.complianceTableData.length === 0) {
              this.totalRows = 0;
              this.tableErrorMessage = 'noDataAvailable';
            }
            if (response.hasOwnProperty("total")) {
              this.totalRows = response.data.total;
            } else {
              this.totalRows = this.complianceTableData.length;
            }
          } catch (e) {
            this.tableDataLoaded = true;
            this.tableErrorMessage = this.errorHandling.handleJavascriptError(e);
          }
        },
        (error) => {
          this.tableDataLoaded = true;
          this.tableErrorMessage = "apiResponseError";
          this.logger.log("error", error);
        }
      );
  }

  getRouteQueryParameters(): any {
    this.activatedRouteSubscription = this.activatedRoute.queryParams.subscribe((params) => {
      if (this.selectedAssetGroup && this.selectedDomain) {
        this.updateComponent();
      }
    });
  }

  massageData(data) {
    const refactoredService = this.refactorFieldsService;
    const columnNamesMap = this.columnNamesMap;
    const newData = [];
    data.map(function (row) {
      const KeysTobeChanged = Object.keys(row);
      let newObj = {};
      KeysTobeChanged.forEach((element) => {
        let elementnew;
        if (columnNamesMap[element]) {
          elementnew = columnNamesMap[element];
          newObj = Object.assign(newObj, { [elementnew]: row[element] });
        } else {
          elementnew =
            refactoredService.getDisplayNameForAKey(element.toLocaleLowerCase()) ||
            element;
          newObj = Object.assign(newObj, { [elementnew]: row[element] });
        }
        // change data value
        newObj[elementnew] = DATA_MAPPING[
          typeof newObj[elementnew] == 'string'
            ? newObj[elementnew].toLowerCase()
            : newObj[elementnew]
        ]
          ? DATA_MAPPING[newObj[elementnew].toLowerCase()]
          : newObj[elementnew];
      });
      newObj['Compliance'] = newObj['assetsScanned'] == 0 ? 'NR' : newObj['Compliance'] + '%';
      newData.push(newObj);
    });
    return newData;
  }

  goToDetails(event) {
    const selectedRow = event.rowSelected;
    const data = event.data;
    this.tableScrollTop = event.tableScrollTop;
    this.selectedRowIndex = event.selectedRowIndex;
    this.storeState(data);
    try {
      this.workflowService.addRouterSnapshotToLevel(
        this.router.routerState.snapshot.root,
        0,
        this.breadcrumbPresent,
      );
      const updatedQueryParams = { ...this.activatedRoute.snapshot.queryParams };
      updatedQueryParams['searchValue'] = undefined;
      this.router.navigate(['../policy-details', selectedRow['Policy ID'].valueText], {
        relativeTo: this.activatedRoute,
        queryParams: updatedQueryParams,
        queryParamsHandling: 'merge',
      });
    } catch (error) {
      this.errorMessage = this.errorHandling.handleJavascriptError(error);
      this.logger.log('error', error);
    }
  }

  callNewSearch(searchVal) {
    this.searchTxt = searchVal;
    this.isStatePreserved = false;
    this.tableDataLoaded = false;
    this.getData();
  }

  calculateDate(_JSDate) {
    if (!_JSDate) {
      return 'No Data';
    }
    const date = new Date(_JSDate);
    const year = date.getFullYear().toString();
    const month = date.getMonth() + 1;
    let monthString;
    if (month < 10) {
      monthString = '0' + month.toString();
    } else {
      monthString = month.toString();
    }
    const day = date.getDate();
    let dayString;
    if (day < 10) {
      dayString = '0' + day.toString();
    } else {
      dayString = day.toString();
    }
    return monthString + '-' + dayString + '-' + year;
  }

  handlePopClick(event) {
    const fileType = "csv";

    try {
      const queryParams = {
        fileFormat: fileType,
        serviceId: 2,
        fileType,
      };

      const filterToBePassed = this.getFilterPayloadForDataAPI();

      const downloadRequest = {
        ag: this.selectedAssetGroup,
        filter: {
          domain: this.selectedDomain,
        },
        reqFilter: filterToBePassed,
        from: 0,
        searchtext: event.searchTxt,
        size: this.totalRows,
      };

      const downloadUrl = environment.download.url;
      const downloadMethod = environment.download.method;

      this.downloadService.requestForDownload(
        queryParams,
        downloadUrl,
        downloadMethod,
        downloadRequest,
        "Policy Compliance Overview",
        this.totalRows
      );
    } catch (error) {
      this.logger.log("error", error);
    }
  }

  dropDashboardItem({
    container,
    previousIndex,
    currentIndex,
  }: CdkDragDrop<DashboardArrangementItems>) {
    moveItemInArray(container.data, previousIndex, currentIndex);
    this.dashboardArrangementService.saveArrangement(this.dashboardContainers);
  }

  toggleContainer(index: number) {
    if (index === DashboardContainerIndex.ASSET_GRAPH && this.isCollapsedContainer(index)) {
      this.windowExpansionService.status.next(true);
    }

    this.dashcobardCollapsedContainers = {
      ...this.dashcobardCollapsedContainers,
      ...{ [index]: !this.isCollapsedContainer(index) },
    };
    this.dashboardArrangementService.saveCollapsed(this.dashcobardCollapsedContainers);
  }

  isCollapsedContainer(index: number) {
    return this.dashcobardCollapsedContainers[index];
  }

  collapsedContainerTitle(index: number) {
    return this.dashcobardCollapsedContainersTitles[index];
  }

  ngOnDestroy() {
    try {
      if (this.agDomainSubscription) {
        this.agDomainSubscription.unsubscribe();
      }
      if (this.onFilterChange) {
        this.onFilterChange.unsubscribe();
      }
      if (this.routeSubscription) {
        this.routeSubscription.unsubscribe();
      }
      if (this.complianceTableSubscription) {
        this.complianceTableSubscription.unsubscribe();
      }
      if (this.subscriptionDomain) {
        this.subscriptionDomain.unsubscribe();
      }
      if (this.issueFilterSubscription) {
        this.issueFilterSubscription.unsubscribe();
      }
      if (this.activatedRouteSubscription) {
        this.activatedRouteSubscription.unsubscribe();
      }
      if (this.trendDataSubscription) {
        this.trendDataSubscription.unsubscribe();
      }
    } catch (error) {
      this.logger.log('error', error);
    }
  }

  onresize(event): void {
    this.breakpoint1 = event.target.innerWidth <= 1000 ? 2 : 4;
    this.breakpoint2 = event.target.innerWidth <= 800 ? 1 : 2;
    this.breakpoint3 = event.target.innerWidth <= 400 ? 1 : 1;
    this.breakpoint4 = event.target.innerWidth <= 400 ? 1 : 1;
  }
}
