import {RxHR} from "@akanass/rx-http-request";
import {Observable} from "rxjs";
import {Logger} from "./logger";
import {Config} from "./config";
const restify = require('restify');

const logger = Logger.getLogger(__filename);

export class RestClient {

	private config;

  constructor(
    private isClient: boolean,
    private token: string,
    private serviceLocatorUri?: string) {

    if (!isClient) {
      this.config = new Config();
      this.serviceLocatorUri = this.config.get(Config.KEY_URL_INT_SERVICE_LOCATOR);
    }
  }

  getToken(username: string, password: string): Observable<any> {

    return this.getAuthUri()
      .map(authUri => authUri + '/tokens/')
      .mergeMap(uri => this.post(uri, this.getBasicAuthHeader(username, password)))
      .map(data => JSON.parse(data));
  }

  getSessions(): Observable<any[]> {
    return this.getSessionDbUri()
      .mergeMap(sessionDbUri => this.getJson(sessionDbUri + '/sessions/', this.token));
  }

  getSession(sessionId: string): Observable<any> {
    return this.getSessionDbUri()
      .mergeMap(sessionDbUri => this.getJson(sessionDbUri + '/sessions/' + sessionId, this.token));
  }

  postSession(session: any) {
    return this.getSessionDbUri()
      .mergeMap(sessionDbUri => this.postJson(sessionDbUri + '/sessions/', this.token, session));
  }

  deleteSession(sessionId: any) {
    return this.getSessionDbUri()
      .mergeMap(sessionDbUri => this.deleteWithToken(sessionDbUri + '/sessions/' + sessionId, this.token));
  }

	getDatasets(sessionId): Observable<any> {
		return this.getSessionDbUri().mergeMap(sessionDbUri => {
			return this.getJson(sessionDbUri + '/sessions/' + sessionId + '/datasets/', this.token);
		});
	}

	getDataset(sessionId, datasetId): Observable<any> {
		return this.getSessionDbUri().mergeMap(sessionDbUri => {
			return this.getJson(sessionDbUri + '/sessions/' + sessionId + '/datasets/' + datasetId, this.token);
		});
	}

	getFile(sessionId, datasetId, maxLength) {
    // Range request 0-0 would produce 416 - Range Not Satifiable
	  if (maxLength === 0) {
	    return Observable.of("");
    }

		return this.getFileBrokerUri().mergeMap(fileBrokerUri => {
			return this.getWithToken(
				fileBrokerUri + '/sessions/' + sessionId + '/datasets/' + datasetId,
				this.token,
				{Range: 'bytes=0-' + maxLength});
		});
	}

	getAuthUri() {
		return this.getServiceUri('auth');
	}

	getFileBrokerUri() {
		return this.getServiceUri('file-broker');
	}

	getSessionDbUri() {
		return this.getServiceUri('session-db');
	}

	getServiceUri(serviceName) {
		return this.getJson(this.serviceLocatorUri + '/services', null).map(services => {
			let service = services.filter(service => service.role === serviceName)[0];
			if (!service) {
				Observable.throw(new restify.InternalServerError('service not found' + serviceName));
			}
			return this.isClient ? service.publicUri : service.uri;
		});
	}

	getJson(uri: string, token: string): Observable<any> {
		return this.getWithToken(uri, token).map(data => JSON.parse(data));
	}

	getWithToken(uri: string, token: string, headers?: Object): Observable<string> {
	  if (token) {
      return this.get(uri, this.getBasicAuthHeader('token', token, headers));
    } else {
      return this.get(uri, headers);
    }
	}

  getBasicAuthHeader(username, password, headers?) {
    if (!headers) {
      headers = {};
    }

    headers['Authorization'] = 'Basic ' + new Buffer(username + ':' + password).toString('base64');

    return headers;
  }

	get(uri: string, headers?: Object): Observable<string> {
		let options = {headers: headers};

		logger.debug('get()', uri + ' ' + JSON.stringify(options.headers));

		return RxHR.get(uri, options).map(data => this.handleResponse(data));
	}

  post(uri: string, headers?: Object, body?: Object): Observable<string> {
    let options = {headers: headers, body: body};

    logger.debug('post()', uri + ' ' + JSON.stringify(options.headers));

    return RxHR.post(uri, options).map(data => this.handleResponse(data));
  }

  postJson(uri: string, token: string, data: any): Observable<string> {
    let headers = this.getBasicAuthHeader('token', token);
    headers['content-type'] = 'application/json';
    return this.post(uri, headers, JSON.stringify(data));
  }

  deleteWithToken(uri: string, token: string) {
    return this.delete(uri, this.getBasicAuthHeader('token', token));
  }

  delete(uri: string, headers?: Object): Observable<any> {
    let options = {headers: headers};
    return RxHR.delete(uri, options);
  }

	handleResponse(data) {
    if (data.response.statusCode >= 200 && data.response.statusCode <= 299) {
      logger.debug('response', data.body);
      return data.body;
    } else {
      if (data.response.statusCode >= 400 && data.response.statusCode <= 499) {
        logger.debug('error', data.response.statusCode + ' ' + data.response.statusMessage + ' ' + data.response.body);
        throw this.responseToError(data.response);
      } else {
        logger.error('error', data.response.statusCode + ' ' + data.response.statusMessage + ' ' + data.response.body);
        throw new restify.InternalServerError("unable to get the dataset from the session-db");
      }
    }
  }

	responseToError(response) {
	  if (this.isClient) {
	    return new Error(response.statusCode + ' - ' + response.statusMessage + ' (' + response.body + ') ' + response.request.href);
    } else {
      return new restify.HttpError({
        restCode: response.statusMessage,
        statusCode: response.statusCode,
        message: response.body
      });
    }
	}
}
