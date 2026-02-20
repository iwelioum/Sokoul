<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
*
* @since 1.0.0
*
*/

if(!class_exists('Zetafields')){

    /* The class
	========================================================
	*/
    class Zetafields {

        // Attributes
        public $args = false;


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function __construct($args) {
            // Generate fields
            if(is_array($args)){
                foreach($args as $item ){
                    $this->fields_html($item);
                }
            }
        }


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function meta($meta_key) {
            global $post;
            $field = get_post_meta($post->ID, $meta_key, true);
            return esc_attr($field);
        }


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function fields_html($item) {
            if(is_array($item)){
                // Get Type
                $type = zeta_isset($item,'type');
                // Compose field from type
                switch($type){
                    case 'number':
                        $output = $this->text($item);
                        break;
                    case 'text':
                        $output = $this->text($item);
                        break;
                    case 'textarea':
                        $output = $this->textarea($item);
                        break;
                    case 'date':
                        $output = $this->tdate($item);
                        break;
                    case 'generator':
                        $output = $this->generator($item);
                        break;
                    case 'checkbox':
                        $output = $this->checkbox($item);
                        break;
                    case 'upload':
                        $output = $this->upload($item);
                        break;
                    case 'upload_single':
                        $output = $this->upload_single($item);
                        break;
					case 'upload_multi':
                        $output = $this->upload_multi($item);
                        break;						
                    case 'heading':
                        $output = $this->heading($item);
                        break;
                }
                // Compose view
                $response = apply_filters('zetaflix_metafields', $output, $type);
                // Echo View
                echo $response;
            }
        }


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function text($args){
            // Parameters
            $label = zeta_isset($args,'label');
            $id    = zeta_isset($args,'id');
            $id2   = zeta_isset($args,'id2');
            $class = zeta_isset($args,'class');
            $fdesc = zeta_isset($args,'fdesc');
            $desc  = zeta_isset($args,'desc');
            $doubl = zeta_isset($args,'double');
            // Values
            $value1 = $id ? $this->meta($id) : false;
            $value2 = $id2 ? $this->meta($id2) : false;
            // View
            $output  = "<tr id='{$id}_box'><td class='label'><label for='{$id}'>{$label}</label>";
    		$output .= "<p class='description'>{$desc}</p></td>";
    		$output .= "<td class='field'>";
            if(!empty($doubl)){
                $output .= "<input class='extra-small-text' type='text' name='{$id}' id='{$id}' value='{$value1}' data-original='{$value1}'> - ";
                $output .= "<input class='extra-small-text' type='text' name='{$id2}' id='{$id2}' value='{$value2}' data-original='{$value2}'>";
            } else {
                $output .= "<input class='{$class}' type='text' name='{$id}' id='{$id}' value='{$value1}' data-original='{$value1}'>";
            }
            if(!empty($fdesc)) $output .= "<p class='description'>{$fdesc}</p>";

            $output .= "</td></tr>";
            // Compose view
            return $output;
        }


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function textarea($args) {
            // Parameters
            $id     = zeta_isset($args,'id');
            $desc   = zeta_isset($args,'desc');
            $upload = zeta_isset($args,'upload');
            $aid    = zeta_isset($args,'aid');
            $label  = zeta_isset($args,'label');
            $rows   = zeta_isset($args,'rows');
            $value  = $id ? $this->meta($id) : false;
            $btnt   = $upload ? __z('Upload') : false;
            // View
            $output  = "<tr id='{$id}_box'><td class='label'><label for='{$id}'>{$label}</label>";
    		$output .= "<p class='description'>{$desc}</p></td>";
    		$output .= "<td class='field'><textarea name='{$id}' id='{$id}' rows='{$rows}'>{$value}</textarea>";
            if(!empty($upload)) $output .= "<input class='{$aid} button-secondary' type='button' value='{$btnt}' />";
    		$output .= "</td></tr>";
            // Compose view
            return $output;
        }


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function tdate($args){
            // Parameters
            $id    = zeta_isset($args,'id');
            $label = zeta_isset($args,'label');
            $fdesc = zeta_isset($args,'fdesc');
            $value = $id ? $this->meta($id) : false;
            // View
            $output  = "<tr id='{$id}_box'>";
    		$output .= "<td class='label'><label for='{$id}'>{$label}</label></td>";
    		$output .= "<td class='field'>";
            $output .= "<input class='small-text' type='date' name='{$id}' id='{$id}' value='{$value}'>";
            if(!empty($fdesc)) $output .= "<p class='description'>{$fdesc}</p>";
            $output .= "</td></tr>";
            // Compose view
            return $output;
        }


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function generator($args) {
            // Parameters
			
			global $pagenow;
			
			if ('post-new.php' == $pagenow || 'post.php' == $pagenow && isset($_GET['post']) && $_GET['action'] == 'edit'){
				$posttype = (isset($_GET['post_type'])) ? $_GET['post_type'] : null;
				$postt = (isset($_GET['post'])) ? $_GET['post'] : null;
				if($posttype == 'tvshows' || $posttype == 'seasons' || $posttype == 'episodes' || get_post_type($postt) == 'tvshows'|| get_post_type($postt) == 'seasons' || get_post_type($postt) == 'episodes'){ 
					$fieldtype = 'number';
				}else{
					$fieldtype = 'text';
				}
			}
			
			
            // Parameters
            $id           = zeta_isset($args,'id');
            $id2          = zeta_isset($args,'id2');
            $id3          = zeta_isset($args,'id3');
            $label        = zeta_isset($args,'label');
            $desc         = zeta_isset($args,'desc');
            $style        = zeta_isset($args,'style');
            $fdesc        = zeta_isset($args,'fdesc');
            $class        = zeta_isset($args,'class');
            $placeholder  = zeta_isset($args,'placeholder');
            $placeholder2 = zeta_isset($args,'placeholder2');
            $placeholder3 = zeta_isset($args,'placeholder3');
            $requireupdat = zeta_isset($args,'requireupdate');
            $onlyupdatepo = zeta_isset($args,'previewpost');
            $editoraction = zeta_isset($_GET,'action');
            $text_buttom  = ($editoraction == 'edit') ? __('Update info') : __('Generate');
            if(!$onlyupdatepo){
                $acti_buttom  = ($editoraction == 'edit') ? 'omegadb-updaterpost' : 'omegadb-generartor';
            } else {
                $acti_buttom = 'omegadb-updaterpost';
            }

            $text_duplic  = __z('Check duplicate content');
            // Values
            $value1 = $id  ? $this->meta($id)  : false;
            $value2 = $id2 ? $this->meta($id2) : false;
            $value3 = $id3 ? $this->meta($id3) : false;
            // View
            $output  = "<tr id='{$id}_box'><td class='label'>";
    		$output .= "<label for='{$id}'>{$label}</label>";
    		$output .= "<p class='description'>{$desc}</p></td>";
            $output .= "<td {$style} class='field'>";
            if(!empty($id)) $output .= "<input class='{$class}' type='{$fieldtype}' name='{$id}' id='{$id}' placeholder='{$placeholder}' value='{$value1}'> ";
            if(!empty($id2)) $output .= "<input class='{$class}' type='number' name='{$id2}' id='{$id2}' placeholder='{$placeholder2}' value='{$value2}'> ";
            if(!empty($id3)) $output .= "<input class='{$class}' type='number' name='{$id3}' id='{$id3}' placeholder='{$placeholder3}' value='{$value3}'> ";
            if(!$editoraction || $requireupdat == true){
                $output .= "<input type='button' class='button button-primary' name='omegadb-generartor' id='{$acti_buttom}' value='{$text_buttom}'>";
            }
    		$output .= "<p class='description'>{$fdesc}</p>";
    		$output .= "</td></tr>";
            // Compose view
            return $output;
        }


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function checkbox($args) {
            // Parameters
            $id      = zeta_isset($args,'id');
            $label   = zeta_isset($args,'label');
            $clabel  = zeta_isset($args,'clabel');
            $checked = $this->meta($id) == true ? ' checked' : false;
            // View
            $output  = "<tr id='{$id}_box'><td class='label'><label>{$label}</label></td>";
            $output .= "<td class='field'><label for='{$id}_clik'><input type='checkbox' name='{$id}' value='1' id='{$id}_clik'{$checked}> {$clabel}</label></td></tr>";
            // Compose view
            return $output;
        }


        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function upload($args){
            global $post;
            // Parameters
            $id      = zeta_isset($args,'id');
            $aid     = zeta_isset($args,'aid');
            $label   = zeta_isset($args,'label');
            $desc    = zeta_isset($args,'desc');
            $ajax    = zeta_isset($args,'ajax');
            $prelink = zeta_isset($args,'prelink');
            $nonce   = wp_create_nonce('zt-ajax-upload-image');
            $value   = $id ? $this->meta($id) : false;
            $btntext = __z('Upload now');
            $btnuplo = __z('Upload');
            // View
            $output  = "<tr id='{$id}_box'><td class='label'><label for='zt_poster'>{$label}</label><p class='description'>{$desc}</p></td>";
    		$output .= "<td class='field'><input class='regular-text' type='text' name='{$id}' id='{$id}' value='{$value}'> ";
    		$output .= "<input class='{$aid} button-secondary' type='button' value='{$btnuplo}' /> ";
            if(!empty($ajax) && !filter_var($value, FILTER_VALIDATE_URL)) {
                $output .= "<input class='import-upload-image button-secondary' type='button' data-field='{$id}' data-postid='{$post->ID}' data-nonce='{$nonce}' data-prelink='{$prelink}' value='{$btntext}' />";
            }
    		$output .= "</td></tr>";
            // Compose View
            return $output;
        }
		
        public function upload_single($args){
            global $post;
            // Parameters
            $id      = zeta_isset($args,'id');
            $label   = zeta_isset($args,'label');
            $desc    = zeta_isset($args,'desc');
            $value   = $id ? $this->meta($id) : false;
            $btntext = __z('Upload Image');
			
            // View
            $output  = "<tr id='{$id}_box'><td class='label'><label for='{$id}'>{$label}</label><p class='description'>{$desc}</p></td>";
    		$output .= "<td class='field'><input class='regular-text' type='text' name='{$id}' id='{$id}' value='{$value}'> ";
		$output .= "<a href='#' class='zt_upload_image_button button button-secondary' data-id='{$id}'>{$btntext}</a>";
    		$output .= "</td></tr>";
            // Compose View
            return $output;
        }
		

        public function upload_multi($args){
            global $post;
			
			
			
				$image = 'Upload Image';
				$image_str = '';
				$image_size = 'full';
				$display = 'none';
	
			
            // Parameters
            $id      = zeta_isset($args,'id');
			$rows      = zeta_isset($args,'rows');
            $label   = zeta_isset($args,'label');
            $desc    = zeta_isset($args,'desc');
            $ajax    = zeta_isset($args,'ajax');
            $prelink = zeta_isset($args,'prelink');
            $nonce   = wp_create_nonce('zt-ajax-upload-image');
            $value   = $id ? $this->meta($id) : false;
			$value = explode(',', $value);
            $btntext = __z('Upload now');
            $btnuplo = __z('Upload');
            // View
            $output  = "<tr id='{$id}_box'><td class='label'><label for='{$id}'>{$label}</label><p class='description'>{$desc}</p></td>";
    		$output .= "<td class='field'> ";
			$output .= "<textarea name='{$id}' id='{$id}' rows='{$rows}' >".esc_attr(implode(',', $value))."</textarea>";
			$output .= "<a href='#' class='wc_multi_upload_image_button button' data-id='{$id}'>{$image}</a>";
			$output .= '<input type="hidden" class="attechments-ids ' . isset($name) . '" name="' . isset($name) . '" id="' . isset($name) . '" value="' . esc_attr(implode(',', $value)) . '" />';
    		$output .= "</td></tr>";
            // Compose View
            return $output;
        }

        /**
         * @since 1.0.0
         * @version 1.1
         */
        public function heading($args) {
            // Parameters
            $colspan = zeta_isset($args,'colspan');
            $text    = zeta_isset($args,'text');
            // View
            return "<tr><td colspan={$colspan}><h3>{$text}</h3></td></tr>";
        }
    }
}
