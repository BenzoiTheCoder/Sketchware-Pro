package pro.sketchware.activities.coloreditor;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Xml;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.besome.sketch.lib.base.BaseAppCompatActivity;
import com.besome.sketch.lib.ui.ColorPickerDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Objects;

import a.a.a.xB;
import mod.hey.studios.code.SrcCodeEditor;
import pro.sketchware.R;
import pro.sketchware.activities.coloreditor.adapters.ColorsAdapter;
import pro.sketchware.activities.coloreditor.models.ColorItem;
import pro.sketchware.databinding.ColorEditorActivityBinding;
import pro.sketchware.databinding.ColorEditorAddBinding;
import pro.sketchware.lib.validator.ColorInputValidator;
import pro.sketchware.utility.FileUtil;
import pro.sketchware.utility.PropertiesUtil;
import pro.sketchware.utility.SketchwareUtil;
import pro.sketchware.utility.XmlUtil;

public class ColorEditorActivity extends BaseAppCompatActivity {

    private static final int MENU_SAVE = 0;
    private static final int MENU_OPEN_IN_EDITOR = 1;
    public static String contentPath;
    private final ArrayList<ColorItem> colorList = new ArrayList<>();
    private boolean isGoingToEditor;
    private ColorEditorActivityBinding binding;
    private ColorsAdapter adapter;
    private Activity activity;
    private ColorPickerDialog colorpicker;
    private String title;
    private String xmlPath;

    public static String getColorValue(Context context, String colorValue, int referencingLimit) {
        if (colorValue == null || referencingLimit <= 0) {
            return null;
        }

        if (colorValue.startsWith("#")) {
            return colorValue;
        }
        if (colorValue.startsWith("?attr/")) {
            return getColorValueFromXml(context, colorValue.substring(6), referencingLimit - 1);
        }
        if (colorValue.startsWith("@color/")) {
            return getColorValueFromXml(context, colorValue.substring(7), referencingLimit - 1);

        } else if (colorValue.startsWith("@android:color/")) {
            return getColorValueFromSystem(colorValue, context);
        }
        return "#ffffff";
    }

    public static String getColorValueFromSystem(String colorValue, Context context) {
        String colorName = colorValue.substring(15);
        int colorId = context.getResources().getIdentifier(colorName, "color", "android");
        try {
            int colorInt = ContextCompat.getColor(context, colorId);
            return String.format("#%06X", (0xFFFFFF & colorInt));
        } catch (Exception e) {
            e.printStackTrace();
            return "#ffffff";
        }
    }

    private static String getColorValueFromXml(Context context, String colorName, int referencingLimit) {
        try {
            String clrXml = FileUtil.readFileIfExist(contentPath);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(clrXml));
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && "color".equals(parser.getName())) {
                    String nameAttribute = parser.getAttributeValue(null, "name");
                    if (colorName.equals(nameAttribute)) {
                        String colorValue = parser.nextText().trim();
                        if (colorValue.startsWith("@")) {
                            return getColorValue(context, colorValue, referencingLimit - 1);
                        } else {
                            return colorValue;
                        }
                    }
                }
                eventType = parser.next();
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static String convertListToXml(ArrayList<ColorItem> colorList) {
        try {
            XmlSerializer xmlSerializer = Xml.newSerializer();
            StringWriter stringWriter = new StringWriter();

            xmlSerializer.setOutput(stringWriter);
            xmlSerializer.startDocument("UTF-8", true);
            xmlSerializer.text("\n");
            xmlSerializer.startTag(null, "resources");
            xmlSerializer.text("\n");

            for (ColorItem colorItem : colorList) {
                xmlSerializer.startTag(null, "color");
                xmlSerializer.attribute(null, "name", colorItem.getColorName());
                xmlSerializer.text(colorItem.getColorValue());
                xmlSerializer.endTag(null, "color");
                xmlSerializer.text("\n");
            }

            xmlSerializer.endTag(null, "resources");
            xmlSerializer.text("\n");
            xmlSerializer.endDocument();

            return stringWriter.toString();

        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        enableEdgeToEdgeNoContrast();
        super.onCreate(savedInstanceState);
        binding = ColorEditorActivityBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());
        ViewCompat.setOnApplyWindowInsetsListener(binding.recyclerviewColors, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), systemBars.bottom);
            return insets;
        });
        initialize();
    }

    private void initialize() {
        activity = this;
        contentPath = getIntent().getStringExtra("content");
        title = getIntent().getStringExtra("title");
        xmlPath = getIntent().getStringExtra("xml");
        colorpicker = new ColorPickerDialog(this, 0xFFFFFFFF, false, false);

        setSupportActionBar(binding.toolbar);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);
        binding.toolbar.setNavigationOnClickListener(_v -> onBackPressed());

        parseColorsXML(FileUtil.readFile(contentPath));
        binding.recyclerviewColors.setLayoutManager(new LinearLayoutManager(this));

        adapter = new ColorsAdapter(colorList, this);
        binding.recyclerviewColors.setAdapter(adapter);

        binding.addColorButton.setOnClickListener(v -> showColorEditDialog(null, -1));

        binding.recyclerviewColors.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);

                if (dy < 0) {
                    if (!binding.addColorButton.isExtended()) {
                        binding.addColorButton.extend();
                    }
                } else if (dy > 0) {
                    if (binding.addColorButton.isExtended()) {
                        binding.addColorButton.shrink();
                    }
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isGoingToEditor) {
            parseColorsXML(FileUtil.readFile(contentPath));
            adapter.notifyDataSetChanged();
        }
        isGoingToEditor = false;
    }

    @Override
    public void onBackPressed() {
        String originalXml = FileUtil.readFile(contentPath);
        String newXml = convertListToXml(colorList);
        if (!Objects.equals(XmlUtil.replaceXml(newXml), XmlUtil.replaceXml(originalXml))) {
            showExitDialog();
        } else {
            super.onBackPressed();
        }
        if (colorList.isEmpty() && (!originalXml.contains("</resources>"))) {
            XmlUtil.saveXml(contentPath, newXml);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(android.view.Menu menu) {
        menu.add(0, MENU_SAVE, 0, "Save")
                .setIcon(R.drawable.ic_mtrl_save)
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_ALWAYS);

        menu.add(0, MENU_OPEN_IN_EDITOR, 0, "Open in editor")
                .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_NEVER);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == MENU_SAVE) {
            XmlUtil.saveXml(contentPath, convertListToXml(colorList));
        } else if (id == MENU_OPEN_IN_EDITOR) {
            XmlUtil.saveXml(contentPath, convertListToXml(colorList));
            Intent intent = new Intent();
            intent.setClass(getApplicationContext(), SrcCodeEditor.class);
            intent.putExtra("title", title);
            intent.putExtra("content", contentPath);
            intent.putExtra("xml", xmlPath);
            isGoingToEditor = true;
            startActivity(intent);
        }
        return super.onOptionsItemSelected(item);
    }

    private void showExitDialog() {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setTitle(xB.b().a(activity, R.string.common_word_warning));
        dialog.setMessage(xB.b().a(activity, R.string.src_code_editor_unsaved_changes_dialog_warning_message));
        dialog.setPositiveButton(xB.b().a(activity, R.string.common_word_save), (v, which) -> {
            XmlUtil.saveXml(contentPath, convertListToXml(colorList));
            v.dismiss();
            finish();
        });
        dialog.setNegativeButton(xB.b().a(activity, R.string.common_word_exit), (v, which) -> {
            v.dismiss();
            finish();
        });
        dialog.show();
    }

    private void parseColorsXML(String colorXml) {
        try {
            colorList.clear();
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(colorXml));

            int eventType = parser.getEventType();
            String colorName = null;
            String colorValue = null;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                String tagName = parser.getName();
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("color".equals(tagName)) {
                            colorName = parser.getAttributeValue(null, "name");
                        }
                        break;
                    case XmlPullParser.TEXT:
                        colorValue = parser.getText();
                        break;
                    case XmlPullParser.END_TAG:
                        if ("color".equals(tagName)) {
                            if ((colorName != null) && PropertiesUtil.isHexColor(getColorValue(getApplicationContext(), colorValue, 4))) {
                                colorList.add(new ColorItem(colorName, colorValue));
                            }
                        }
                        break;
                }
                eventType = parser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void showDeleteDialog(int position) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(activity);
        dialog.setIcon(R.drawable.ic_mtrl_delete);
        dialog.setTitle(xB.b().a(activity, R.string.color_editor_delete_color));
        dialog.setMessage(xB.b().a(activity, R.string.picker_color_message_delete_all_custom_color));
        dialog.setPositiveButton(xB.b().a(activity, R.string.common_word_delete), (v, which) -> {
            colorList.remove(position);
            adapter.notifyItemRemoved(position);
            adapter.notifyItemRangeChanged(position, colorList.size());
            v.dismiss();
        });
        dialog.setNegativeButton(xB.b().a(activity, R.string.common_word_cancel), (v, which) -> v.dismiss());
        dialog.show();
    }

    public void showColorEditDialog(ColorItem colorItem, int position) {
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(this);
        ColorEditorAddBinding dialogBinding = ColorEditorAddBinding.inflate(LayoutInflater.from(this));
        ColorInputValidator colorValidator = new ColorInputValidator(this, dialogBinding.colorValueInputLayout, dialogBinding.colorPreview);

        if (colorItem != null) {
            dialogBinding.colorKeyInput.setText(colorItem.getColorName());
            dialogBinding.colorPreview.setBackgroundColor(PropertiesUtil.parseColor(getColorValue(activity.getApplicationContext(), colorItem.getColorValue(), 3)));

            if (colorItem.getColorValue().startsWith("@")) {
                dialogBinding.colorValueInput.setText(colorItem.getColorValue().replace("@", ""));
                dialogBinding.hash.setText("@");
                dialogBinding.colorValueInput.setEnabled(false);
                dialogBinding.hash.setEnabled(false);
                dialogBinding.colorValueInputLayout.setError(null);
            } else {
                dialogBinding.colorValueInput.setText(colorItem.getColorValue().replace("#", ""));
                dialogBinding.hash.setText("#");

            }

            dialog.setTitle("Edit color");

        } else {
            dialog.setTitle("Add new color");
            dialogBinding.colorPreview.setBackgroundColor(0xFFFFFF);
        }

        dialog.setPositiveButton("Save", (v1, which) -> {
            String key = Objects.requireNonNull(dialogBinding.colorKeyInput.getText()).toString();
            String value = Objects.requireNonNull(dialogBinding.colorValueInput.getText()).toString();

            if (key.isEmpty() || value.isEmpty()) {
                SketchwareUtil.toast("Please fill in all fields", Toast.LENGTH_SHORT);
                return;
            }

            if (value.startsWith("#")) {
                if (!PropertiesUtil.isHexColor(value)) {
                    SketchwareUtil.toast("Please enter a valid HEX color", Toast.LENGTH_SHORT);
                }
                return;
            }

            if (colorItem != null) {
                colorItem.setColorName(key);

                if (dialogBinding.hash.getText().equals("@")) {
                    colorItem.setColorValue("@" + value);
                } else {
                    colorItem.setColorValue("#" + value);
                }

                adapter.notifyItemChanged(position);
            } else {
                addColor(key, value);
            }
            v1.dismiss();
        });

        dialogBinding.colorPreviewCard.setOnClickListener(v -> {
            colorpicker.a(new ColorPickerDialog.b() {
                @Override
                public void a(int colorInt) {
                    String selectedColorHex = "#" + String.format("%06X", colorInt & 0x00FFFFFF);
                    dialogBinding.colorPreviewCard.setCardBackgroundColor(PropertiesUtil.parseColor(selectedColorHex));
                    dialogBinding.colorValueInput.setText(selectedColorHex.replace("#", ""));
                    dialogBinding.colorValueInput.setEnabled(true);
                    dialogBinding.hash.setEnabled(true);
                    dialogBinding.hash.setText("#");
                }

                @Override
                public void a(String var1, int var2) {
                }
            });
            colorpicker.showAtLocation(v, Gravity.CENTER, 0, 0);
        });

        if (colorItem != null) {
            dialog.setNeutralButton("Delete", (v1, which) -> {
                colorList.remove(position);
                adapter.notifyItemRemoved(position);
                adapter.notifyItemRangeChanged(position, colorList.size());
                v1.dismiss();
            });
        }

        dialog.setNegativeButton(getString(R.string.cancel), (v1, which) -> v1.dismiss());
        dialog.setView(dialogBinding.getRoot());
        dialog.show();
    }

    private void addColor(String name, String value) {
        ColorItem newItem = new ColorItem(name, "#" + value);
        for (int i = 0; i < colorList.size(); i++) {
            if (colorList.get(i).getColorName().equals(name)) {
                colorList.set(i, newItem);
                adapter.notifyItemChanged(i);
                return;
            }
        }
        colorList.add(newItem);
        adapter.notifyItemInserted(colorList.size() - 1);
    }
}
