<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/

function zt_comments_args( $args = array() ){

	$comments_args = array(
		'style'       => '',
		'avatar_size' => 60,
		'callback'    => 'zt_theme_comment_template'
	);

	return wp_parse_args( $args, $comments_args );
}


function zt_theme_comment_template($comment, $args, $depth) {
	$GLOBALS['comment'] = $comment;
	extract($args, EXTR_SKIP);
	$tag =  ('div' == $args['style'] ) ? 'div' : 'li';
	$add_below = 'comment-inner';
	
?>
<div <?php if ( $comment->comment_approved == '0') { $pending = ' pending'; } else { $pending = null;  }?> <?php comment_class( empty( $args['has_children'] ) ? 'comments-comment'.$pending : 'parent comments-comment'.$pending) ?>  id="comment-<?php comment_ID() ?>">
          <div class="comment-left">
            <div class="comment-avatar">
             <?php if ( $args['avatar_size'] != 0 ) echo get_avatar( $comment, $args['avatar_size'] ); ?>
            </div>
          </div>
          <div class="comment-right">
		  <div id="comment-inner-<?php comment_ID() ?>">
            <div class="comment-data">
              <div class="comment-head">
                <a href="#"><span class="user-name"><?php if( $comment->user_id > 0 ){ echo ''. get_user_option('display_name', $comment->user_id ) .''; }?></span></a>
                <span class="user-date"><?php printf( __z('%1$s'), get_comment_date() ); ?></span>
              </div>
              <div class="comment-message">
			  <?php if ( $comment->comment_approved == '0') { ?>
			  <span class="pending-notice"><?php _z('Your comment is awaiting moderation.');?></span>
			  <?php }?>
			  <?php comment_text(); ?>
			  </div>    
            </div>
            <div class="comment-btn"><?php if(is_user_logged_in()) { comment_reply_link( array_merge( $args, array( 'add_below' => $add_below, 'depth' => $depth, 'max_depth' => $args['max_depth'] )) ); } ?></div>  
				</div>
          </div>
        </div>












<?php }


// Form comments
function zt_theme_comments_args(){
	$backlink = esc_url(wp_login_url().'?redirect_to='.get_permalink());
	$backlink = '<div class="comments-notice">You must be <a href="'.$backlink.'">logged in</a> to post a comment.</div>';
	$commenter = wp_get_current_commenter();
	$required =  ' <em class="text-red" title="'. __z('Required') .'">*</em>';
	$comments_args = array(
		'label_submit'         => __z('Post comment'),
		'title_reply'          => '',
		'logged_in_as'         => '',
		'class_submit' => 'field-btn-submit',
		'cancel_reply_link' => __z('Cancel'),
		'must_log_in' => $backlink,
		'comment_notes_after'  => '',
		'comment_notes_before' => '',
		'class_form' =>  'comments-form',
		'comment_field' => '
			<div class="comments-form-field">
				<textarea id="comment" name="comment" required="true" class="normal" placeholder="'. __z('Write a comment..') .'"></textarea>
			</div>
		',
		'fields' => apply_filters('comment_form_default_fields', array(
			'author' => '
				<div class="comments-form-field half">
						<input name="author" type="text" class="fullwidth" value="'.esc_attr($commenter['comment_author']).'" required="true" placeholder="'.__z('Display Name').'"/>
				</div>
			',
			'email' => '
				<div class="comments-form-field half fix">
						<input name="email" type="text" class="fullwidth" value="'.esc_attr($commenter['comment_author_email']).'" required="true" placeholder="'.__z('Email Address').'"/>
				</div>
			')
		)
	);
	return $comments_args;
}
