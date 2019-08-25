package elektra.erik.com

import ai.fritz.core.Fritz
import ai.fritz.fritzvisionobjectmodel.FritzVisionObjectPredictor
import ai.fritz.vision.inputs.FritzVisionImage
import android.graphics.Bitmap
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicYuvToRGB
import android.util.Log
import com.google.firebase.database.*
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {

    private lateinit var renderScript: RenderScript
    private lateinit var yuvToRGB: ScriptIntrinsicYuvToRGB
    private var yuvDataLength: Int = 0
    private lateinit var allocationIn: Allocation
    private lateinit var allocationOut: Allocation
    private lateinit var bitmapOut: Bitmap
    private lateinit var database: DatabaseReference
    private val itemMap by lazy {
        hashMapOf<String, Int>()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Fritz.configure(this)
        val filteredObjects = mutableListOf<String>()
        filteredObjects.add("cell phone")
        filteredObjects.add("smartphone")
        filteredObjects.add("laptop")
        filteredObjects.add("person")


        val objectPredictor = FritzVisionObjectPredictor.getInstance(this)
        var fritzVisionImage: FritzVisionImage

        database = FirebaseDatabase.getInstance().getReference("users")
        val query = database.orderByChild("full_name").equalTo("Memo Vera")

        cameraView.addFrameProcessor {

            if (yuvDataLength == 0) {
                initializeData()
            }

            allocationIn.copyFrom(it.data)
            yuvToRGB.forEach(allocationOut)
            allocationOut.copyTo(bitmapOut)
            fritzVisionImage = FritzVisionImage.fromBitmap(bitmapOut, it.rotation)
            val visionObjects = objectPredictor.predict(fritzVisionImage)

            itemMap.clear()

            visionObjects.forEach { visionObject ->
                if (filteredObjects.contains(visionObject.visionLabel.text)) {
                    Log.d("vision", visionObject.visionLabel.text)
                    Log.d("item", itemMap.keys.toString())
                    if (itemMap.containsKey(visionObject.visionLabel.text)) {
                        itemMap[visionObject.visionLabel.text] = itemMap[visionObject.visionLabel.text]!! + 1
                    } else {
                        itemMap[visionObject.visionLabel.text] = 1
                    }
                }
            }

            runOnUiThread {
                tvDetectedItem.text = ""
                itemMap.forEach { map ->
                    tvDetectedItem.append("Detected ${map.value} ${map.key}\n")
                    query.addListenerForSingleValueEvent(object : ValueEventListener {
                        override fun onDataChange(dataSnapshot: DataSnapshot) {
                            var isTech = 0
                            val children = dataSnapshot.children
                            if (map.key.equals("cell phone")) {
                                isTech = 1
                            }
                            children.forEach {
                                //Log.d("data", it.toString())
                                database.child(it.key.toString()).child("is_tech").setValue(isTech)
                                //Log.d("data", it.child("is_tech").value.toString())
                            }

                        }

                        override fun onCancelled(databaseError: DatabaseError) {

                        }
                    })
                }
            }
        }
    }

    private fun initializeData() {
        yuvDataLength = cameraView.previewSize?.height!! * cameraView.previewSize?.width!! * 3 / 2
        renderScript = RenderScript.create(baseContext)
        yuvToRGB = ScriptIntrinsicYuvToRGB.create(renderScript, Element.U8_4(renderScript))
        allocationIn = Allocation.createSized(renderScript, Element.U8(renderScript), yuvDataLength)
        bitmapOut = Bitmap.createBitmap(
            cameraView.previewSize?.width!!,
            cameraView.previewSize?.height!!,
            Bitmap.Config.ARGB_8888
        )
        allocationOut = Allocation.createFromBitmap(renderScript, bitmapOut)
        yuvToRGB.setInput(allocationIn)
    }

    override fun onStart() {
        super.onStart()
        cameraView.start()
    }

    override fun onStop() {
        super.onStop()
        cameraView.stop()
    }
}