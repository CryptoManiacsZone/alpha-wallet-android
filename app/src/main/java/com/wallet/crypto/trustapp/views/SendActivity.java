package com.wallet.crypto.trustapp.views;

import android.content.Intent;
import android.graphics.Point;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import com.wallet.crypto.trustapp.R;
import com.wallet.crypto.trustapp.controller.Controller;
import com.wallet.crypto.trustapp.controller.OnTaskCompleted;
import com.wallet.crypto.trustapp.controller.TaskResult;
import com.wallet.crypto.trustapp.controller.TaskStatus;
import com.wallet.crypto.trustapp.controller.Utils;
import com.wallet.crypto.trustapp.model.VMAccount;
import com.wallet.crypto.trustapp.views.barcode.BarcodeCaptureActivity;
import com.google.android.gms.common.api.CommonStatusCodes;
import com.google.android.gms.vision.barcode.Barcode;

import org.ethereum.geth.Address;

import java.util.List;

public class SendActivity extends AppCompatActivity {

    private Controller mController;

    private EditText mTo;
    private EditText mAmount;
    private static final String LOG_TAG = SendActivity.class.getSimpleName();
    private static final int BARCODE_READER_REQUEST_CODE = 1;

    public static final String EXTRA_SENDING_TOKENS = "extra_sending_tokens";
    public static final String EXTRA_CONTRACT_ADDRESS = "extra_contract_address";
    public static final String EXTRA_SYMBOL = "extra_symbol";
    public static final String EXTRA_DECIMALS = "extra_decimals";

    private TextView mResultTextView;

    private boolean mSendingTokens = false;
    private String mContractAddress;
    private String mSymbol;
    private int mDecimals;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_send);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mController = Controller.with(this);

        List<VMAccount> accounts = mController.getAccounts();

        mTo = findViewById(R.id.date);
        mAmount = findViewById(R.id.amount);

        String toAddress = getIntent().getStringExtra(getString(R.string.address_keyword));
        if (toAddress != null) {
            mTo.setText(toAddress);
        }

        mContractAddress = getIntent().getStringExtra(EXTRA_CONTRACT_ADDRESS);
        mDecimals = getIntent().getIntExtra(EXTRA_DECIMALS, -1);
        mSymbol = getIntent().getStringExtra(EXTRA_SYMBOL);
        mSendingTokens = getIntent().getBooleanExtra(EXTRA_SENDING_TOKENS, false);

        assert(!mSendingTokens || (mSendingTokens && mDecimals > -1 && mContractAddress != null));

        EditText amountView = findViewById(R.id.amount);
        if (mSendingTokens && mSymbol != null) {
            amountView.setHint(mSymbol + " amount");
        } else {
            amountView.setHint("ETH amount");
        }

        Button mSendButton = (Button) findViewById(R.id.send_button);
        mSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                // Validate input fields
                boolean inputValid = true;

                final String to = mTo.getText().toString();
                if (!isAddressValid(to)) {
                    mTo.setError("Invalid address");
                    inputValid = false;
                }

                final String amount = mAmount.getText().toString();
                if (!isValidEthAmount(amount)) {
                    mAmount.setError("Invalid amount");
                    inputValid = false;
                }

                if (!inputValid) {
                    return;
                }

                if (mSendingTokens) {
                    mController.clickSendTokens(
                            mController.getCurrentAccount().getAddress(),
                            mTo.getText().toString(),
                            mContractAddress,
                            mAmount.getText().toString(),
                            mDecimals,
                            new OnTaskCompleted() {
                                public void onTaskCompleted(final TaskResult result) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (result.getStatus() == TaskStatus.SUCCESS) {
                                                SendActivity.this.finish();
                                            }
                                            Toast.makeText(SendActivity.this, result.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }
                    );
                } else {
                    mController.clickSend(
                            mController.getCurrentAccount().getAddress(),
                            mTo.getText().toString(),
                            mAmount.getText().toString(),
                            new OnTaskCompleted() {
                                public void onTaskCompleted(final TaskResult result) {
                                    runOnUiThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (result.getStatus() == TaskStatus.SUCCESS) {
                                                SendActivity.this.finish();
                                            }
                                            Toast.makeText(SendActivity.this, result.getMessage(), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }
                            }
                    );
                }
            }
        });

        mResultTextView = (TextView) findViewById(R.id.result_textview);

        ImageButton scanBarcodeButton = (ImageButton) findViewById(R.id.scan_barcode_button);
        scanBarcodeButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(getApplicationContext(), BarcodeCaptureActivity.class);
                startActivityForResult(intent, BARCODE_READER_REQUEST_CODE);
            }
        });
    }

    boolean isAddressValid(String address) {
        try {
            new Address(address);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    boolean isValidEthAmount(String eth) {
        try {
            String wei = Controller.EthToWei(eth);
            return wei != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == BARCODE_READER_REQUEST_CODE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    Barcode barcode = data.getParcelableExtra(BarcodeCaptureActivity.BarcodeObject);

                    String extracted_address = Utils.extractAddressFromQrString(barcode.displayValue);
                    if (extracted_address == null) {
                        Toast.makeText(this, "QR code doesn't contain account address", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    Point[] p = barcode.cornerPoints;
                    mTo.setText(extracted_address);
                } else mResultTextView.setText(R.string.no_barcode_captured);
            } else Log.e(LOG_TAG, String.format(getString(R.string.barcode_error_format),
                    CommonStatusCodes.getStatusCodeString(resultCode)));
        } else super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
