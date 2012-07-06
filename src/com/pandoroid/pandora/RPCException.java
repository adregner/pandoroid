package com.pandoroid.pandora;

public class RPCException extends Exception {
	
	//Error code constants
	//See: http://pan-do-ra-api.wikia.com/wiki/Json/5#Error_codes
	//Global
	public final static int INTERNAL = 0;
	public final static int MAINTENANCE_MODE = 1;
	public final static int URL_PARAM_MISSING_METHOD = 2;
	public final static int URL_PARAM_MISSING_AUTH_TOKEN = 3;
	public final static int URL_PARAM_MISSING_PARTNER_ID = 4;
	public final static int URL_PARAM_MISSING_USER_ID = 5;
	public final static int SECURE_PROTOCOL_REQUIRED = 6;
	public final static int CERTIFICATE_REQUIRED = 7;
	public final static int PARAMETER_TYPE_MISMATCH = 8;
	public final static int PARAMETER_MISSING = 9;
	public final static int PARAMETER_VALUE_INVALID = 10;
	public final static int API_VERSION_NOT_SUPPORTED = 11;
	public final static int LICENSING_RESTRICTIONS = 12;
	public final static int INSUFFICIENT_CONNECTIVITY = 13;
	public final static int UNKNOWN_METHOD_NAME = 14;
	public final static int WRONG_PROTOCOL = 15;
	//Method specific...Hence some are duplicated
	public final static int READ_ONLY_MODE = 1000;
	public final static int INVALID_AUTH_TOKEN = 1001;
	public final static int INVALID_PARTNER_LOGIN = 1002;
	public final static int LISTENER_NOT_AUTHORIZED = 1003;
	public final static int USER_NOT_AUTHORIZED = 1004;
	public final static int MAX_STATIONS_REACHED = 1005;
	public final static int STATION_DOES_NOT_EXIST = 1006;
	public final static int COMPLIMENTARY_PERIOD_ALREADY_IN_USE = 1007;
	public final static int CALL_NOT_ALLOWED = 1008;
	public final static int DEVICE_NOT_FOUND = 1009;
	public final static int PARTNER_NOT_AUTHORIZED = 1010;
	public final static int INVALID_USERNAME = 1011;
	public final static int INVALID_PASSWORD = 1012;
	public final static int USERNAME_ALREADY_EXISTS = 1013;
	public final static int DEVICE_ALREADY_ASSOCIATED_TO_ACCOUNT = 1014;
	public final static int UPGRADE_DEVICE_MODEL_INVALID = 1015;
	public final static int EXPLICIT_PIN_INCORRECT = 1018;
	public final static int EXPLICIT_PIN_MALFORMED = 1020;
	public final static int DEVICE_MODEL_INVALID = 1023;
	public final static int ZIP_CODE_INVALID = 1024;
	public final static int BIRTH_YEAR_INVALID = 1025;
	public final static int BIRTH_YEAR_TOO_YOUNG = 1026;
	public final static int INVALID_COUNTRY_CODE = 1027;
	public final static int INVALID_GENDER = 1027;
	public final static int DEVICE_DISABLED = 1034;
	public final static int DAILY_TRIAL_LIMIT_REACHED = 1035;
	public final static int INVALID_SPONSOR = 1036;
	public final static int USER_ALREADY_USED_TRIAL = 1037;
	//End constants
	
	
	public int code;
	RPCException(int error_code, String message) {
		super(message);
		this.code = error_code;
	}
}
