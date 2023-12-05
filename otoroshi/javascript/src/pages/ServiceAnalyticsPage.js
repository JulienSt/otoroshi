import React, { Component } from 'react';
import * as BackOfficeServices from '../services/BackOfficeServices';
import { ServiceSidebar } from '../components/ServiceSidebar';
import { RoundChart, Histogram } from '../components/recharts';
import { converterBase2 } from 'byte-converter';
import moment from 'moment';

import { OtoDatePicker } from '../components/datepicker';

import DesignerSidebar from './RouteDesigner/Sidebar';

export class ServiceAnalyticsPage extends Component {
  state = {
    service: null,
    data: null,
    from: moment().startOf('day'),
    to: moment(),
    loading: true,
  };

  onRoutes = window.location.pathname.indexOf('/bo/dashboard/routes') === 0;

  sidebarContent(name) {
    if (this.onRoutes) {
      return (
        <DesignerSidebar
          route={{ id: this.props.params.routeId, name }}
          setSidebarContent={this.props.setSidebarContent}
        />
      );
    }
    return (
      <ServiceSidebar
        env={this.state.service.env}
        serviceId={this.props.params.serviceId}
        name={name}
      />
    );
  }

  componentDidMount() {
    this.onRoutes
      ? this.props.setTitle(`Route analytics`)
      : this.props.setTitle(`Service analytics`);
    const fu = this.onRoutes
      ? BackOfficeServices.nextClient.fetch('routes', this.props.params.routeId)
      : BackOfficeServices.fetchService(this.props.params.lineId, this.props.params.serviceId);
    fu.then((service) => {
      this.setState({ service }, () => {
        this.update();
        this.props.setSidebarContent(this.sidebarContent(service.name));
      });
    });
  }

  update = () => {
    this.setState({ loading: true });
    BackOfficeServices.fetchServiceStats(
      this.state.service.id,
      this.state.from,
      this.state.to
    ).then((rawData) => {
      const defaultData = {
        statusesPiechart: { series: [] },
        statusesHistogram: { series: [] },
        durationStats: { series: [] },
        durationPercentiles: { series: [] },
        overheadStats: { series: [] },
        overheadPercentiles: { series: [] },
        dataOutStats: { series: [] },
        dataInStats: { series: [] },
        productPiechart: { series: [] },
        servicePiechart: { series: [] },
        hits: { count: 0 },
        avgDuration: { duration: 0 },
        avgOverhead: { overhead: 0 },
        dataIn: { 'data.dataIn': 0 },
        dataOut: { 'data.dataOut': 0 },
      };
      const data = {
        ...defaultData,
        ...rawData,
      };
      this.setState({ data, loading: false });
    });
  };

  computeValue(value) {
    let unit = 'Mb';
    let computedValue = parseFloat((converterBase2(value, 'B', 'MB') || 0).toFixed(3));
    if (computedValue > 1024.0) {
      computedValue = parseFloat((converterBase2(value, 'B', 'GB') || 0).toFixed(3));
      unit = 'Gb';
    }
    if (computedValue > 1024.0) {
      computedValue = parseFloat((converterBase2(value, 'B', 'TB') || 0).toFixed(3));
      unit = 'Tb';
    }
    if (computedValue > 1024.0) {
      computedValue = parseFloat((converterBase2(value, 'B', 'PB') || 0).toFixed(3));
      unit = 'Pb';
    }
    return `${computedValue.prettify()} ${unit}`;
  }

  row(value, label) {
    return (
      <div key={label}>
        <span>{value}</span>
        <span>{label}</span>
      </div>
    );
  }

  updateDateRange = (from, to) => {
    this.setState({ from, to }, () => {
      this.update();
    });
  };

  render() {
    const defaultData = {
      hits: { count: 0 },
      avgDuration: { duration: 0 },
      avgOverhead: { overhead: 0 },
      dataIn: { 'data.dataIn': 0 },
      dataOut: { 'data.dataOut': 0 },
    };
    const stateData = this.state.data || {};
    const data = {
      ...defaultData,
      ...stateData,
    };

    const hits = data.hits && data.hits.count ? data.hits.count.prettify() : 0;
    const totalDataIn = this.computeValue(data.dataIn['data.dataIn']);
    const totalDataOut = this.computeValue(data.dataOut['data.dataOut']);
    const avgDuration =
      data && data.avgDuration && data.avgDuration.duration
        ? data.avgDuration.duration.toFixed(3)
        : 0;
    const avgOverhead =
      data && data.avgOverhead && data.avgOverhead.overhead
        ? data.avgOverhead.overhead.toFixed(3)
        : 0;

    return (
      <div className="chartsAnalytics">
        <div className="row" style={{ marginBottom: 30, marginLeft: 2 }}>
          <div className="">
            <OtoDatePicker
              updateDateRange={this.updateDateRange}
              from={this.state.from}
              to={this.state.to}
            />
          </div>
        </div>
        {this.state.loading && (
          <div style={{ textAlign: 'center', width: '100%' }}>
            <h3>
              Loading ...{' '}
              <svg
                width="30px"
                height="30px"
                xmlns="http://www.w3.org/2000/svg"
                viewBox="0 0 100 100"
                preserveAspectRatio="xMidYMid"
                className="uil-ring"
              >
                <rect x="0" y="0" width="100" height="100" fill="none" className="bk" />
                <defs>
                  <filter id="uil-ring-shadow" x="-100%" y="-100%" width="300%" height="300%">
                    <feOffset result="offOut" in="SourceGraphic" dx="0" dy="0" />
                    <feGaussianBlur result="blurOut" in="offOut" stdDeviation="0" />
                    <feBlend in="SourceGraphic" in2="blurOut" mode="normal" />
                  </filter>
                </defs>
                <path
                  fill="#b5b3b3"
                  d="M10,50c0,0,0,0.5,0.1,1.4c0,0.5,0.1,1,0.2,1.7c0,0.3,0.1,0.7,0.1,1.1c0.1,0.4,0.1,0.8,0.2,1.2c0.2,0.8,0.3,1.8,0.5,2.8 c0.3,1,0.6,2.1,0.9,3.2c0.3,1.1,0.9,2.3,1.4,3.5c0.5,1.2,1.2,2.4,1.8,3.7c0.3,0.6,0.8,1.2,1.2,1.9c0.4,0.6,0.8,1.3,1.3,1.9 c1,1.2,1.9,2.6,3.1,3.7c2.2,2.5,5,4.7,7.9,6.7c3,2,6.5,3.4,10.1,4.6c3.6,1.1,7.5,1.5,11.2,1.6c4-0.1,7.7-0.6,11.3-1.6 c3.6-1.2,7-2.6,10-4.6c3-2,5.8-4.2,7.9-6.7c1.2-1.2,2.1-2.5,3.1-3.7c0.5-0.6,0.9-1.3,1.3-1.9c0.4-0.6,0.8-1.3,1.2-1.9 c0.6-1.3,1.3-2.5,1.8-3.7c0.5-1.2,1-2.4,1.4-3.5c0.3-1.1,0.6-2.2,0.9-3.2c0.2-1,0.4-1.9,0.5-2.8c0.1-0.4,0.1-0.8,0.2-1.2 c0-0.4,0.1-0.7,0.1-1.1c0.1-0.7,0.1-1.2,0.2-1.7C90,50.5,90,50,90,50s0,0.5,0,1.4c0,0.5,0,1,0,1.7c0,0.3,0,0.7,0,1.1 c0,0.4-0.1,0.8-0.1,1.2c-0.1,0.9-0.2,1.8-0.4,2.8c-0.2,1-0.5,2.1-0.7,3.3c-0.3,1.2-0.8,2.4-1.2,3.7c-0.2,0.7-0.5,1.3-0.8,1.9 c-0.3,0.7-0.6,1.3-0.9,2c-0.3,0.7-0.7,1.3-1.1,2c-0.4,0.7-0.7,1.4-1.2,2c-1,1.3-1.9,2.7-3.1,4c-2.2,2.7-5,5-8.1,7.1 c-0.8,0.5-1.6,1-2.4,1.5c-0.8,0.5-1.7,0.9-2.6,1.3L66,87.7l-1.4,0.5c-0.9,0.3-1.8,0.7-2.8,1c-3.8,1.1-7.9,1.7-11.8,1.8L47,90.8 c-1,0-2-0.2-3-0.3l-1.5-0.2l-0.7-0.1L41.1,90c-1-0.3-1.9-0.5-2.9-0.7c-0.9-0.3-1.9-0.7-2.8-1L34,87.7l-1.3-0.6 c-0.9-0.4-1.8-0.8-2.6-1.3c-0.8-0.5-1.6-1-2.4-1.5c-3.1-2.1-5.9-4.5-8.1-7.1c-1.2-1.2-2.1-2.7-3.1-4c-0.5-0.6-0.8-1.4-1.2-2 c-0.4-0.7-0.8-1.3-1.1-2c-0.3-0.7-0.6-1.3-0.9-2c-0.3-0.7-0.6-1.3-0.8-1.9c-0.4-1.3-0.9-2.5-1.2-3.7c-0.3-1.2-0.5-2.3-0.7-3.3 c-0.2-1-0.3-2-0.4-2.8c-0.1-0.4-0.1-0.8-0.1-1.2c0-0.4,0-0.7,0-1.1c0-0.7,0-1.2,0-1.7C10,50.5,10,50,10,50z"
                  filter="url(#uil-ring-shadow)"
                  transform="rotate(204 50 50)"
                >
                  <animateTransform
                    attributeName="transform"
                    type="rotate"
                    from="0 50 50"
                    to="360 50 50"
                    repeatCount="indefinite"
                    dur="1s"
                  />
                </path>
              </svg>
            </h3>
          </div>
        )}
        {!this.state.loading && (
          <div className="row">
            <div className="col-md-6">
              <h4>Informations</h4>
              <table className="fulltable table table-bordered table-striped table-condensed table-hover">
                <tbody>
                  <tr>
                    <td>{this.row(hits, ' hits')}</td>
                  </tr>
                  <tr>
                    <td>{this.row(totalDataIn, ' in')}</td>
                  </tr>
                  <tr>
                    <td>{this.row(totalDataOut, ' out')}</td>
                  </tr>
                  <tr>
                    <td>{this.row(avgDuration, ' ms. average duration')}</td>
                  </tr>
                  <tr>
                    <td>{this.row(avgOverhead, ' ms. average overhead')}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div className="col-md-6">
              <RoundChart
                series={data.statusesPiechart && data.statusesPiechart.series}
                title="Http statuses"
                size={200}
              />
            </div>
          </div>
        )}
        {!this.state.loading && [
          <div className="row">
            <div className="col-md-12">
              <Histogram
                series={data.statusesHistogram && data.statusesHistogram.series}
                title="Http statuses"
              />
            </div>
          </div>,
          <div className="row">
            <div className="col-md-12">
              <Histogram
                series={data.durationStats && data.durationStats.series}
                title="Duration"
                unit=" millis."
              />
            </div>
          </div>,
          <div className="row">
            <div className="col-md-12">
              <Histogram
                series={data.durationPercentiles && data.durationPercentiles.series}
                title="Duration percentiles"
                unit=" millis."
              />
            </div>
          </div>,
          <div className="row">
            <div className="col-md-12">
              <Histogram
                series={data.overheadStats && data.overheadStats.series}
                title="Overhead"
                unit=" millis."
              />
            </div>
          </div>,
          <div className="row">
            <div className="col-md-12">
              <Histogram
                series={data.overheadPercentiles && data.overheadPercentiles.series}
                title="Overhead percentiles"
                unit=" millis."
              />
            </div>
          </div>,
          <div className="row">
            <div className="col-md-12">
              <Histogram
                series={data.dataInStats && data.dataInStats.series}
                title="Data In"
                unit=" bytes"
              />
            </div>
          </div>,
          <div className="row">
            <div className="col-md-12">
              <Histogram
                series={data.dataOutStats && data.dataOutStats.series}
                title="Data In"
                unit=" bytes"
              />
            </div>
          </div>,
          <div className="row">
            <div className="col-md-12">
              <RoundChart
                series={data.apiKeyPiechart && data.apiKeyPiechart.series}
                title="Hits by apikey"
                unit=" hits"
                size={500}
              />
            </div>
          </div>,
          this.state.service.privateApp ? (
            <div className="row">
              <div className="col-md-12">
                <RoundChart
                  series={data.userPiechart && data.userPiechart.series}
                  title="Hits by user"
                  unit=" hits"
                  size={500}
                />
              </div>
            </div>
          ) : null,
        ]}
      </div>
    );
  }
}
