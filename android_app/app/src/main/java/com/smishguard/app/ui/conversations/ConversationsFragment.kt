package com.smishguard.app.ui.conversations

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.smishguard.app.databinding.FragmentConversationsBinding

/*
 * ConversationsFragment.kt — Conversations List Screen
 * ======================================================
 * A "Fragment" is a reusable portion of a screen. It has its own
 * lifecycle, layout, and behavior, but lives INSIDE an Activity.
 *
 * WHY use Fragments instead of multiple Activities?
 *   - Fragments can be combined on larger screens (tablets)
 *   - Navigation between Fragments is smoother
 *   - Fragments share data more easily through shared ViewModels
 *
 * RECYCLERVIEW explained:
 *   RecyclerView is Android's efficient scrolling list. Instead of creating
 *   a View for EVERY item (which would use lots of memory), it creates
 *   only enough Views to fill the screen, then RECYCLES them as you scroll.
 *
 *   Components:
 *   - RecyclerView: The scrolling container
 *   - LayoutManager: Decides how items are arranged (linear, grid, etc.)
 *   - Adapter: Binds data to the Views (ConversationAdapter)
 *   - ViewHolder: Holds references to the Views in each item
 *
 * FRAGMENT LIFECYCLE (different from Activity):
 *   onCreateView()  → Inflate and return the layout
 *   onViewCreated() → Set up the UI (safe to access views)
 *   onDestroyView() → Clean up view references
 *
 * "_binding" vs "binding":
 *   _binding is nullable (null before onCreateView and after onDestroyView)
 *   binding is a non-null accessor (throws if called at the wrong time)
 */
class ConversationsFragment : Fragment() {

    private var _binding: FragmentConversationsBinding? = null
    // "?" makes it nullable — can be null
    private val binding get() = _binding!!
    // "get() = _binding!!" is a custom GETTER — every time you access
    // "binding", it returns the non-null version of _binding.
    // "!!" asserts non-null — safe because we only use it between
    // onCreateView and onDestroyView.

    private lateinit var viewModel: ConversationsViewModel
    private lateinit var adapter: ConversationAdapter

    /**
     * Inflate the fragment's layout XML.
     *
     * "LayoutInflater" converts XML layout files into actual View objects.
     * "container" is the parent ViewGroup this fragment will be inserted into.
     * "false" means "don't attach to parent yet" — the FragmentManager does that.
     */
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentConversationsBinding.inflate(inflater, container, false)
        return binding.root
    }

    /**
     * Called after onCreateView. Safe to set up the UI here.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Get the ViewModel scoped to this Fragment
        viewModel = ViewModelProvider(this)[ConversationsViewModel::class.java]

        setupRecyclerView()
        setupSwipeRefresh()
        observeViewModel()

        // Load conversations when the fragment is first shown
        viewModel.loadConversations()
    }

    /**
     * Set up the RecyclerView with a LinearLayoutManager and our custom adapter.
     */
    private fun setupRecyclerView() {
        adapter = ConversationAdapter { conversation ->
            // This lambda is the "onItemClick" callback.
            // When a user taps a conversation, this code runs.
            // "conversation" is the SmsConversation that was tapped.
            Toast.makeText(
                requireContext(),
                "Tapped: ${conversation.senderName ?: conversation.senderAddress}",
                Toast.LENGTH_SHORT
            ).show()
            // Future: Navigate to a detail screen showing all messages
        }

        binding.recyclerViewConversations.apply {
            // "apply { }" executes this block on the RecyclerView object
            layoutManager = LinearLayoutManager(requireContext())
            // LinearLayoutManager arranges items in a vertical scrolling list
            adapter = this@ConversationsFragment.adapter
            // "this@ConversationsFragment.adapter" explicitly refers to the
            // Fragment's adapter property (not the RecyclerView's adapter property)
        }
    }

    /**
     * Set up pull-to-refresh functionality.
     */
    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            viewModel.loadConversations()
        }
    }

    /**
     * Observe LiveData from the ViewModel and update the UI when data changes.
     *
     * "viewLifecycleOwner" scopes the observation to this Fragment's VIEW lifecycle.
     * This is preferred over "this" (the Fragment) because a Fragment's view
     * can be destroyed and recreated while the Fragment itself survives.
     */
    private fun observeViewModel() {
        viewModel.conversations.observe(viewLifecycleOwner) { conversations ->
            adapter.submitList(conversations)
            // "submitList" is a DiffUtil-aware method that efficiently updates
            // only the items that changed (instead of refreshing the entire list)

            // Show "no conversations" message if the list is empty
            binding.textEmptyState.visibility = if (conversations.isEmpty()) {
                View.VISIBLE
            } else {
                View.GONE    // GONE = invisible AND takes no space in the layout
            }
        }

        viewModel.isLoading.observe(viewLifecycleOwner) { isLoading ->
            binding.swipeRefreshLayout.isRefreshing = isLoading
            // When true, shows the pull-to-refresh spinner
        }

        viewModel.error.observe(viewLifecycleOwner) { errorMessage ->
            errorMessage?.let {
                Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
            }
        }
    }

    /**
     * Clean up the binding reference to prevent memory leaks.
     * After this, the binding is null and should not be accessed.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
