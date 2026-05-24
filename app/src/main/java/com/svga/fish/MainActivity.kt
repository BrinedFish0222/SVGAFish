package com.svga.fish

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        title = getString(R.string.examples_hub_title)

        val exampleListView = findViewById<RecyclerView>(R.id.exampleListView)
        val examples = ExampleHubEntries.items

        exampleListView.layoutManager = LinearLayoutManager(this)
        exampleListView.adapter = ExampleHubAdapter(
            examples = examples,
            titleProvider = ::getString,
            onExampleSelected = { example ->
                startActivity(Intent(this, example.destination))
            }
        )
    }

}

internal class ExampleHubAdapter(
    private val examples: List<ExampleEntry>,
    private val titleProvider: (Int) -> String,
    private val onExampleSelected: (ExampleEntry) -> Unit
) : RecyclerView.Adapter<ExampleHubAdapter.ExampleViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ExampleViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)
        return ExampleViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ExampleViewHolder, position: Int) {
        val example = examples[position]
        holder.bind(
            example = example,
            title = titleProvider(example.titleResId),
            onExampleSelected = onExampleSelected
        )
    }

    override fun getItemCount(): Int = examples.size

    internal class ExampleViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val titleView: TextView = itemView.findViewById(android.R.id.text1)

        fun bind(
            example: ExampleEntry,
            title: String,
            onExampleSelected: (ExampleEntry) -> Unit
        ) {
            titleView.text = title
            itemView.setOnClickListener {
                onExampleSelected(example)
            }
        }
    }
}

internal object ExampleHubEntries {
    val items: List<ExampleEntry> = listOf(
        ExampleEntry(
            titleResId = R.string.example_animation_from_network,
            destination = AnimationFromNetworkActivity::class.java
        ),
        ExampleEntry(
            titleResId = R.string.example_deduplicated_network_loads,
            destination = DeduplicatedNetworkLoadsActivity::class.java
        ),
        ExampleEntry(
            titleResId = R.string.example_grid_svga,
            destination = GridSvgaDemoActivity::class.java
        ),
        ExampleEntry(
            titleResId = R.string.example_old_api_grid_svga,
            destination = OldApiGridSvgaDemoActivity::class.java
        ),
        ExampleEntry(
            titleResId = R.string.example_old_api_deduplicated_network_loads,
            destination = OldApiDeduplicatedNetworkLoadsActivity::class.java
        )
    )
}

internal data class ExampleEntry(
    val titleResId: Int,
    val destination: Class<out Activity>
)
