<?php
/**
 * The template for displaying comments
 *
 * This is the template that displays the area of the page that contains both the current comments
 * and the comment form.
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

/* Exit if accessed directly */
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

$fmovie_comments = get_option('admin_comments'); 
if ($fmovie_comments == 1) { 

?>
<script>var comments = "<?php echo disqus; ?>";</script>
<section id="comment" class="bl">
	<div class="heading simple">
		<h2 class="title"><?php echo txtcomments; ?></h2>
	</div>
	<div class="content">
		<div id="disqus_thread" style="min-height: 100px;">
			<div id="disqus_thread_loader"><?php echo esc_html__( 'Loading Comments', 'fmovie' ); ?></div>
		</div>
	</div>
</section><!-- #comments -->
<?php } else { ?>
<?php if ( post_password_required() ) { return; } ?>

<section id="comments" class="comments-area bl">
	<?php if ( have_comments() ) { ?>
		<h4 class="comments-title"><?php comments_number(esc_html__('No Comments', 'fmovie'), esc_html__('1 Comment', 'fmovie'), '% ' . esc_html__('Comments', 'fmovie') ); ?></h4>
		<span class="title-line"></span>
		<ol class="comment-list">
			<?php wp_list_comments( array( 'avatar_size' => 60, 'style' => 'ul', 'callback' => 'fmovie_comments', 'type' => 'all' ) ); ?>
		</ol>
		<?php the_comments_pagination( array( 'prev_text' => '<i class="fa fa-angle-left"></i> <span class="screen-reader-text">' . esc_html__( 'Previous', 'fmovie') . '</span>', 'next_text' => '<span class="screen-reader-text">' . esc_html__( 'Next', 'fmovie') . '</span> <i class="fa fa-angle-right"></i>', ) ); ?>
	<?php } ?>
	<?php if ( ! comments_open() && get_comments_number() && post_type_supports( get_post_type(), 'comments' ) ) { ?>
		<p class="no-comments"><?php _e( 'Comments are closed.', 'fmovie'); ?></p>
	<?php } ?>
	<?php comment_form(); ?>
</section><!-- #comments -->
<?php } ?>


